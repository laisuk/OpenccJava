const status = document.querySelector('#status');
const button = document.querySelector('#convert');
const backend = new URLSearchParams(location.search).get('backend') || 'js';
try {
    let api;
    if (backend === 'js') {
        api = await import('./js/openccjava.js');
    } else if (backend === 'wasm') {
        await new Promise((resolve, reject) => {
            const script = document.createElement('script');
            script.src = './wasm-gc/openccjava.wasm-runtime.js';
            script.onload = resolve;
            script.onerror = () => reject(new Error('Could not load Wasm GC runtime'));
            document.head.append(script);
        });
        api = (await TeaVM.wasmGC.load('./wasm-gc/openccjava.wasm')).exports;
    } else {
        throw new Error('Unknown backend: ' + backend);
    }
    if (!api.initialize()) throw new Error(api.getLastError());
    function convert(text, config, punctuation = false) {
        const result = api.convert(text, config, punctuation);
        if (result === null) throw new Error(api.getLastError());
        return result;
    }
    const cases = [
        ['汉字转换测试', 's2t', '漢字轉換測試'],
        ['漢字轉換測試', 't2s', '汉字转换测试'],
        // Hair/development disambiguation requires phrase dictionaries.
        ['头发发展', 's2t', '頭髮發展'],
        ['頭髮發展', 't2s', '头发发展'],
        ['“汉字”', 's2t', '「漢字」', true],
        ['「漢字」', 't2s', '“汉字”', true],
        ['软件鼠标', 's2twp', '軟體滑鼠'],
        ['舊字體：廣國，讀賣。', 't2jp', '旧字体：広国，読売。'],
        ['你好，小篆国际编码18', 's2seal', '你𿒛，𽌠𽴖𾇓𿭖𿛛碼18'],
        ['你𿒛，𽌠𽴖𾇓𿭖𿛛碼18', 'seal2s', '你好，小篆国际编码18'],
        ['汉字转换测试。'.repeat(1500), 's2t', '漢字轉換測試。'.repeat(1500)]
    ];
    for (const [input, config, expected, punctuation = false] of cases) {
        const actual = convert(input, config, punctuation);
        if (actual !== expected) throw new Error(`${config} regression failed: ${actual.slice(0, 80)}`);
    }
    const configs = Array.from(api.getSupportedConfigs());
    if (configs.length !== 24 || configs.some(config => !api.isValidConfig(config))) {
        throw new Error('Supported configuration enumeration failed');
    }
    for (const config of configs) convert('汉字漢字🙂', config);
    if (api.normalizeCompatExtended('聼竒金鼖🙂') !== '聽奇金鼖🙂') throw new Error('Normalization failed');
    if (api.detofu('骖𬴂🙂') !== '骖騑🙂') throw new Error('DeTofu failed');
    if (api.zhoCheck('汉字转换测试') !== 2 || api.zhoCheck('漢字轉換測試') !== 1 || api.zhoCheck('ABC🙂') !== 0) {
        throw new Error('Chinese detection failed');
    }
    if (api.convert('test', 'invalid', false) !== null || !api.getLastError()) {
        throw new Error('Invalid configuration did not report an error');
    }
    status.textContent = `${backend}: dictionary initialization and ${cases.length} conversions passed; all 24 configs, normalization, DeTofu, detection and error reporting passed.`;
    document.documentElement.dataset.smoke = 'passed';
    button.disabled = false;
    button.addEventListener('click', () => {
        try {
            document.querySelector('#output').value = convert(
                document.querySelector('#input').value,
                document.querySelector('#config').value,
                document.querySelector('#punctuation').checked);
            status.textContent = `${backend}: conversion succeeded`;
        } catch (error) { status.textContent = String(error); }
    });
} catch (error) {
    document.documentElement.dataset.smoke = 'failed';
    status.textContent = `${backend}: ${error}`;
    console.error(error);
}
