function updateSort(button) {
    const field = button.dataset.field;
    let sortBy = document.getElementById('sortByInput').value.split(',').filter(e => e);
    let sortDir = document.getElementById('sortDirectionInput').value.split(',').filter(e => e);

    const fieldIndex = sortBy.indexOf(field);
    if (fieldIndex !== -1) {
        if (sortDir[fieldIndex] === 'desc' && field.valueOf() !== 'hot') {
            sortDir[fieldIndex] = 'asc';
        } else if (sortDir[fieldIndex] === 'asc' || field.valueOf() === 'hot') {
            sortBy.splice(fieldIndex, 1);
            sortDir.splice(fieldIndex, 1);
        }
    } else {
        sortBy.push(field);
        sortDir.push('desc');
    }
    /*console.log(sortBy)
    console.log(sortDir)*/
    document.getElementById('sortByInput').value = sortBy.join(',');
    document.getElementById('sortDirectionInput').value = sortDir.join(',');

    document.getElementById('filterForm').submit();
}