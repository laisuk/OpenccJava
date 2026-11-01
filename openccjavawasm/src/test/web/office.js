import adapter from './openccJavaAdapter.js';
const status = document.getElementById('status');
window.packageResults = [];
let checks = 0;
function assert(value, message) { if (!value) throw new Error(message); checks++; }
function base64(bytes) {
    let text = '';
    for (let i = 0; i < bytes.length; i += 8192) text += String.fromCharCode(...bytes.subarray(i, i + 8192));
    return btoa(text);
}
try {
    await adapter.initialize();
    const cases = await (await fetch('./office-fixtures/manifest.json')).json();
    for (const test of cases) {
        const input = new Uint8Array(await (await fetch(`./office-fixtures/sample.${test.format}`)).arrayBuffer());
        const padded = new Uint8Array(input.length + 8); padded.set(input, 8);
        const output = adapter.convertOfficeBytes(padded.subarray(8), test.format, 's2t', test.punctuation, test.keepFont);
        assert(output instanceof Uint8Array && output.length > 22, `Byte transfer ${test.name}`);
        const blob = new Blob([output]);
        const url = URL.createObjectURL(blob);
        const downloaded = new Uint8Array(await (await fetch(url)).arrayBuffer());
        URL.revokeObjectURL(url);
        assert(downloaded.length === output.length && downloaded.every((byte, i) => byte === output[i]), `Blob bytes ${test.name}`);
        window.packageResults.push({name: test.name, bytes: base64(downloaded)});
    }
    const docx = await (await fetch('./office-fixtures/sample.docx')).arrayBuffer();
    const regional = adapter.convertOfficeBytes(docx, 'docx', 's2twp', true, true);
    window.packageResults.push({name: 'regional.docx', bytes: base64(regional)});
    assert(regional.length > 22, 'ArrayBuffer and regional configuration');
    const nonAsciiEpub = await (await fetch('./office-fixtures/nonascii.epub')).arrayBuffer();
    const corruptDocx = await (await fetch('./office-fixtures/corrupt.docx')).arrayBuffer();
    for (const [input, format, config] of [
        [new Uint8Array([80, 75, 1]), 'docx', 's2t'],
        [docx.slice(0, docx.byteLength - 12), 'docx', 's2t'],
        [docx, 'pdf', 's2t'], [docx, 'docx', 'invalid'], [nonAsciiEpub, 'epub', 's2t'],
        [corruptDocx, 'docx', 's2t']
    ]) {
        let failed = false;
        try { adapter.convertOfficeBytes(input, format, config); }
        catch { failed = true; }
        assert(failed && adapter.getLastError(), 'Invalid input must not return bytes');
    }
    document.documentElement.dataset.office = 'passed';
    status.textContent = `${adapter.getBackend()}: ${checks} Office checks passed; 29 Blob packages ready for independent validation`;
} catch (error) {
    document.documentElement.dataset.office = 'failed';
    status.textContent = `${checks} checks completed: ${error.stack || error}`;
}
