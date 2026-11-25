// Optional read-only browser check. Serve the existing SPA build on the test Gateway origin.
const { chromium } = require('playwright');
const { readFile } = require('node:fs/promises');
const path = require('node:path');

async function check() {
  const [dist, gateway] = process.argv.slice(2);
  const browser = await chromium.launch({
    executablePath: process.env.STAND_BROWSER || undefined,
    headless: true
  });
  try {
    const page = await browser.newPage();
    await page.route(gateway + '/**', async route => {
      const pathname = new URL(route.request().url()).pathname;
      if (pathname.startsWith('/auth-service/') || pathname.startsWith('/swagger/')) {
        return route.continue();
      }
      const asset = /^\/assets\/([\w.-]+)$/.exec(pathname);
      if (asset) {
        const mime = asset[1].endsWith('.css') ? 'text/css' : 'application/javascript';
        return route.fulfill({contentType: mime, body: await readFile(path.join(dist, 'assets', asset[1]))});
      }
      return route.fulfill({contentType: 'text/html', body: await readFile(path.join(dist, 'index.html'))});
    });
    await page.goto(gateway + '/login');
    await page.locator('input[autocomplete="username"]').fill('cycle-admin');
    await page.locator('input[type="password"]').fill(process.env.STAND_QA_LOGIN_PASSWORD);
    await page.locator('button[type="submit"], form button').first().click();
    await page.waitForURL(gateway + '/');
    await page.goto(gateway + '/services');
    for (const name of ['catalog-service', 'inventory-service', 'order-service']) {
      await page.getByText(name, {exact: true}).first().waitFor({state: 'visible'});
    }
  } finally {
    await browser.close();
  }
}
check().catch(error => {console.error(error.message); process.exitCode = 1;});
