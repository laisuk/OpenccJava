import adapter from './openccJavaAdapter.js';
const status = document.getElementById('status');
window.packageResults = [];
const timings = [];
try {
    await adapter.initialize();
    const api = adapter.getBackend() === 'js' ? await import('./js/openccjava.js')
        : (await TeaVM.wasmGC.load('./wasm-gc/openccjava.wasm')).exports;
    const cases = await (await fetch('./office-fixtures/entities/manifest.json')).json();
    for (const test of cases) {
        const bytes = new Uint8Array(await (await fetch(`./office-fixtures/entities/${test.name}.epub`)).arrayBuffer());
        for (const keep of [false, true]) {
            const start = performance.now();
            const output = adapter.convertOfficeBytes(bytes, 'epub', 's2t', false, keep);
            const elapsed = performance.now()-start;
            const text = api.diagnosticZipText(new Int8Array(output.buffer, output.byteOffset, output.byteLength), 'OEBPS/chapter.xhtml');
            if (text !== (keep ? test.expectedKept : test.expectedChanged)) throw new Error(`Content changed unexpectedly: ${test.name}/${keep}`);
            timings.push(`${test.name} keepFont=${keep}: ${elapsed.toFixed(1)}ms`);
            let binary = '';
            for (let i = 0; i < output.length; i += 8192) binary += String.fromCharCode(...output.subarray(i, i+8192));
            window.packageResults.push({name: `${test.name}-${keep}.epub`, bytes: btoa(binary)});
        }
    }
    document.documentElement.dataset.entities = 'passed';
    status.textContent = `${adapter.getBackend()}: 16 entity/font packages passed (unknown and malformed entities preserved)\n` + timings.join('\n');
} catch (error) {
    document.documentElement.dataset.entities = 'failed'; status.textContent = String(error) + '\n' + timings.join('\n');
}
