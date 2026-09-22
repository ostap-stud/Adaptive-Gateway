document.querySelector('#theme-toggle-btn').addEventListener('click', function () {
    var wasDark = localStorage.getItem('theme') === 'dark';
    localStorage.setItem('theme', wasDark ? 'light' : 'dark');
    document.documentElement.setAttribute('data-bs-theme', wasDark ? 'light' : 'dark');
});
