import {validateModernUI} from "./modern-ui.js";
import adapter, {createAdapter} from './openccJavaAdapter.js';
import {editorLeft, editorRight, setInputEditorText, getEditorText, setEditorText,
    repairGbMojibake, convertOfficeFile} from './app.js';
import {detectCjkEncoding} from './cjkEncodingDetector.js';

const el = id => document.getElementById(id);
let checks = 0;
window.packageResults = [];
function assert(condition, message) {
    if (!condition) throw new Error(message);
    checks++;
}
const tick = () => new Promise(resolve => requestAnimationFrame(() => setTimeout(resolve, 100)));
try {
    assert(document.documentElement.dataset.ready === 'true', 'UI initialized');
    const initialStatus = el('out').textContent;
    const versionResponse = await fetch('./core-version.txt');
    assert(versionResponse.ok, 'Gradle version expectation available');
    const coreVersion = await versionResponse.text();
    assert(adapter.getVersion() === coreVersion, 'Backend version matches Core Gradle version');
    assert(initialStatus === `OpenccJava v${coreVersion} loaded. (${adapter.getSupportedConfigs().length} configs available)`,
        'Initialization displays actual Core version and configuration count');
    assert(document.querySelectorAll('.cm-editor').length === 2, 'Two CodeMirror editors');
    assert(document.querySelectorAll('.cm-lineNumbers').length === 2, 'Line numbers');
    assert(el('config').options.length === 24, '24 dropdown configurations');
    assert([...el('config').options].every(option => adapter.isValidConfig(option.value)), 'Valid dropdown values');
    for (const [input, config, expected] of [
        ['汉字转换测试', 's2t', '漢字轉換測試'],
        ['漢字轉換測試', 't2s', '汉字转换测试'],
        ['软件鼠标', 's2twp', '軟體滑鼠'],
        ['舊字體：廣國，讀賣。', 't2jp', '旧字体：広国，読売。'],
        ['你好，小篆国际编码18', 's2seal', '你𿒛，𽌠𽴖𾇓𿭖𿛛碼18']
    ]) {
        setInputEditorText(input);
        el('config').value = config;
        el('config').dispatchEvent(new Event('change'));
        el('btn').click();
        await tick();
        assert(getEditorText(editorRight) === expected, `UI conversion ${config}`);
        assert(el('out').textContent.includes('Throughput:'), 'Conversion timing');
        assert(el('configDescription').textContent.length > 0, 'Configuration description');
    }
    setInputEditorText('“汉字”'); el('config').value = 's2t'; el('punctuation').checked = true;
    el('btn').click(); await tick();
    assert(getEditorText(editorRight) === '「漢字」', 'Punctuation checkbox');
    setInputEditorText('聼竒金鼖🙂'); el('normalizeBtn').click();
    assert(getEditorText(editorLeft) === '聽奇金鼖🙂', 'Normalization UI and supplemental scalars');
    setEditorText(editorRight, '骖𬴂🙂'); el('deTofuBtn').click();
    assert(getEditorText(editorRight) === '骖騑🙂', 'DeTofu UI');
    assert(el('outputCount').textContent === '3', 'Code point character counts');
    assert(adapter.zhoCheck('汉字转换测试') === 2 && adapter.zhoCheck('漢字轉換測試') === 1, 'Language detection');
    let clipboard = '汉字🙂';
    Object.defineProperty(navigator, 'clipboard', {configurable: true, value: {
        readText: async () => clipboard,
        writeText: async value => { clipboard = value; }
    }});
    el('pasteBtn').click(); await tick();
    assert(getEditorText(editorLeft) === clipboard, 'Paste handler (mock browser permission)');
    el('copyBtn').click(); await tick();
    assert(clipboard === '骖騑🙂', 'Copy handler (mock browser permission)');
    el('downloadOutputBtn').click();
    assert(await (await fetch(el('outputDownloadLink').href)).text() === clipboard, 'Output download bytes');
    const fixtures = await (await fetch('./encoding-fixtures.json')).json();
    for (const fixture of fixtures) {
        const bytes = Uint8Array.from(atob(fixture.bytes), ch => ch.charCodeAt(0));
        assert(new TextDecoder(fixture.encoding).decode(bytes) === fixture.text, `Decode ${fixture.encoding}`);
        const transfer = new DataTransfer();
        transfer.items.add(new File([bytes], 'sample.md'));
        el('textEncoding').value = fixture.encoding;
        el('textFile').files = transfer.files;
        el('textFile').dispatchEvent(new Event('change'));
        await tick();
        assert(getEditorText(editorLeft) === fixture.text, `Open file ${fixture.encoding}`);
        assert(el('openedFileName').textContent === 'sample.md', 'Opened filename');
    }
    assert(detectCjkEncoding(new TextEncoder().encode('汉字')).encoding === 'utf-8', 'UTF8 detection');
    assert(repairGbMojibake('ºº×Ö') === '汉字', 'GB mojibake helper');
    setInputEditorText('ºº×Ö'); el('gbMojibakeBtn').click();
    assert(getEditorText(editorLeft) === '汉字', 'GB mojibake UI');
    for (const format of ['docx', 'xlsx', 'pptx', 'odt', 'ods', 'odp', 'epub']) {
        await convertOfficeFile(new File(['not a zip'], `sample.${format}`));
        assert(el('out').textContent.includes('failed') && el('officeResult').hidden
            && !el('officeDownloadBtn').dataset.url, `Reject invalid ${format} without a download`);
    }
    await convertOfficeFile(new File(['invalid'], 'sample.pdf'));
    assert(el('out').textContent.includes('Unsupported document format'), 'Unsupported format');
    el('config').value = 's2t';
    for (const [format, keepFont, punctuation] of [['docx', true, true], ['epub', false, false]]) {
        el('keepFont').checked = keepFont; el('punctuation').checked = punctuation;
        const input = await (await fetch(`./office-fixtures/sample.${format}`)).arrayBuffer();
        const transfer = new DataTransfer(); transfer.items.add(new File([input], `sample.${format}`));
        if (format === 'docx') {
            el('officeFile').files = transfer.files;
            el('officeFile').dispatchEvent(new Event('change'));
        } else {
            el('officeDrop').dispatchEvent(new DragEvent('drop', {dataTransfer: transfer, cancelable: true}));
        }
        assert(el('officeDrop').getAttribute('aria-busy') === 'true' && el('config').disabled, 'Busy state before conversion');
        for (let i = 0; i < 100 && el('officeDrop').getAttribute('aria-busy') === 'true'; i++) await tick();
        assert(!el('officeResult').hidden && !el('config').disabled, `File selection/drop ${format}`);
        assert(el('officeDownloadBtn').dataset.filename === `sample_converted.${format}`, 'Download name and extension');
        const bytes = new Uint8Array(await (await fetch(el('officeDownloadBtn').dataset.url)).arrayBuffer());
        let binary = '';
        for (let i = 0; i < bytes.length; i += 8192) binary += String.fromCharCode(...bytes.subarray(i, i + 8192));
        window.packageResults.push({name: `${format}-${keepFont}-${punctuation}.${format}`, bytes: btoa(binary)});
        el('officeDownloadBtn').click();
        assert(el('out').textContent.includes(`Keep font: ${keepFont ? 'enabled' : 'disabled'}`), 'Font option forwarded');
    }
    el('textEncoding').value = 'utf-8';
    await convertOfficeFile(new File(['汉字'], 'sample.txt'));
    assert(!el('officeResult').hidden && el('officeResultText').textContent.includes('sample_converted.txt'), 'TXT drop conversion');
    el('clearInputBtn').click(); el('clearOutputBtn').click();
    assert(getEditorText(editorLeft) === '' && getEditorText(editorRight) === '', 'Clear editors');
    let wasmLoads = 0, jsLoads = 0;
    const fallback = createAdapter({
        wasm: async () => { wasmLoads++; throw new Error('Simulated unsupported GC'); },
        js: async () => { jsLoads++; return import('./js/openccjava.js'); }
    });
    await fallback.initialize('wasm');
    assert(fallback.getBackend() === 'js' && fallback.getBackendNotice().includes('fallback'), 'Visible fallback notice');
    assert(fallback.convert('汉字', 's2t') === '漢字', 'Fallback engine works');
    assert(wasmLoads === 1 && jsLoads === 1, 'Fallback loads each engine once');
    const jsOnly = createAdapter({wasm: async () => { throw new Error('Must not load'); }, js: () => import('./js/openccjava.js')});
    await jsOnly.initialize('js');
    assert(jsOnly.getBackend() === 'js' && !jsOnly.getBackendNotice(), 'Explicit JS loads only JS');
    try { adapter.convert('test', 'invalid'); throw new Error('Accepted invalid config'); }
    catch (error) { assert(adapter.getLastError().includes('Unsupported configuration'), 'Java boundary error'); }
    await validateModernUI(adapter, assert, tick, convertOfficeFile,
        {editorLeft, editorRight, setInputEditorText, getEditorText});
    document.documentElement.dataset.integration = 'passed';
    el('out').textContent = `${adapter.getBackend()}: ${checks} integration checks passed`;
} catch (error) {
    document.documentElement.dataset.integration = 'failed';
    el('out').textContent = `${checks} checks completed: ${error.stack || error}`;
    console.error(error);
}
