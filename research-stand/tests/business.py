"""HTTP acceptance checks against the three backends in a fresh, disposable database."""
from concurrent.futures import ThreadPoolExecutor
from uuid import uuid4


def verify(call, bases, passed):
    catalog, stock, orders = (bases[key] + "/" + key + "-service" for key in ("catalog", "inventory", "order"))

    def item(pid, qty):
        return {"productId": pid, "quantity": qty}

    def quantity(pid):
        return call(stock + "/stock/" + str(pid))["available"]

    product = {"name": "Commerce QA SSD", "categoryId": 2, "price": 27.49}
    pid = call(catalog + "/products", "POST", product, 201)["id"]
    call(catalog + "/products", "POST", dict(product, price=-1), 400)
    call(catalog + "/products", "POST", dict(product, categoryId=7777), 400)
    call(catalog + "/products?page=-1", expected=400)
    call(catalog + "/products?minPrice=99&maxPrice=1", expected=400)
    assert len(call(catalog + "/categories")) >= 3
    page = call(catalog + "/products?categoryId=2&minPrice=27&maxPrice=28&q=qa&page=0&size=1")
    assert [p["id"] for p in page["products"]] == [pid]
    assert not call(catalog + "/products?categoryId=2&q=qa&page=1&size=1")["products"]
    passed("Catalog create, read, pagination, combined filters and validation")

    path = orders + "/orders"
    oid = str(uuid4())
    payload = {"id": oid, "items": [item(pid, 3), item(1, 2)]}
    call(stock + "/stock/" + str(pid), "PUT", {"onHand": 10})
    base = quantity(1)
    purchase = call(path, "POST", payload, 201)
    assert purchase["status"] == "RESERVED" and purchase["total"] == 722.47
    assert quantity(pid) == 7 and quantity(1) == base - 2
    call(catalog + "/products/" + str(pid), "PUT", dict(product, price=88))
    assert call(path, "POST", payload, 201)["total"] == 722.47
    assert call(path + "/" + oid)["items"] == purchase["items"]
    assert any(row["id"] == oid for row in call(path + "?size=1"))
    call(path, "POST", dict(payload, items=[item(pid, 1)]), 409)
    call(stock + "/stock/" + str(pid), "PUT", {"onHand": 2}, 409)
    assert call(path + "/" + oid + "/status", "PATCH", {"status": "CONFIRMED"})["status"] == "CONFIRMED"
    for _ in range(2):
        assert call(path + "/" + oid + "/cancel", "POST")["status"] == "CANCELLED"
    assert quantity(pid) == 10 and quantity(1) == base
    call(path + "/" + oid + "/status", "PATCH", {"status": "CONFIRMED"}, 409)
    passed("Order price snapshot, multi-item reserve, read/list, confirmed status and idempotent cancellation")

    small = {"id": str(uuid4()), "items": [item(1, 1), item(pid, 11)]}
    call(path, "POST", small, 409)
    assert quantity(1) == base and quantity(pid) == 10
    assert call(path + "/" + small["id"])["status"] == "PENDING"
    call(stock + "/stock/" + str(pid), "PUT", {"onHand": 12})
    call(path, "POST", small, 201)
    call(path + "/" + small["id"] + "/cancel", "POST")
    assert quantity(1) == base and quantity(pid) == 12
    call(path, "POST", {"id": str(uuid4()), "items": [item(1, 0)]}, 400)
    passed("Atomic rollback on shortage and retry with the original order ID")

    call(stock + "/stock/" + str(pid), "PUT", {"onHand": 3})
    keys = [str(uuid4()) for _ in range(12)]
    def compete(key):
        return call(stock + "/reservations/" + key, "PUT", {"items": [item(pid, 1)]}, expected=(204, 409))
    with ThreadPoolExecutor(max_workers=12) as pool:
        codes = list(pool.map(compete, keys))
    assert sorted(codes) == [204] * 3 + [409] * 9 and quantity(pid) == 0
    for key, code in zip(keys, codes):
        if code == 204:
            call(stock + "/reservations/" + key, "DELETE", expected=204)
    assert quantity(pid) == 3
    tombstone = stock + "/reservations/" + str(uuid4())
    call(tombstone, "DELETE", expected=204)
    call(tombstone, "PUT", {"items": [item(pid, 1)]}, 409)
    assert call(stock + "/stock/" + str(pid) + "/availability?quantity=3")["available"]
    call(catalog + "/products/" + str(pid), "DELETE", expected=204)
    call(catalog + "/products/" + str(pid), expected=404)
    call(path, "POST", {"id": str(uuid4()), "items": [item(pid, 1)]}, 404)
    passed("12 concurrent reservations for 3 units, release-before-reserve and product deactivation")
