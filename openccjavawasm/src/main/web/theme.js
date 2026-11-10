// Theme changes never recreate editors or disturb their selection/history.
(() => {
    const select = document.getElementById('themePreference');
    const media = matchMedia('(prefers-color-scheme: dark)');
    try { select.value = localStorage.getItem('openccjava-theme') || 'system'; } catch {}
    if (!select.value) select.value = 'system';
    function render() {
        document.documentElement.dataset.theme = select.value === 'system' ? (media.matches ? 'dark' : 'light') : select.value;
    }
    select.addEventListener('change', () => {
        try { localStorage.setItem('openccjava-theme', select.value); } catch {}
        render();
    });
    media.addEventListener('change', render);
    render();
})();
