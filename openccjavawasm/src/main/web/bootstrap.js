// Dynamic import also reports failures to load external CodeMirror dependencies.
import('./app.js').catch(error => {
    document.documentElement.dataset.ready = 'failed';
    document.getElementById('out').textContent = `Failed to load interface: ${error}`;
    document.getElementById('backendStatus').textContent = 'Interface initialization failed.';
    console.error(error);
});
