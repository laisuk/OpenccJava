// TeaVM loading and Java boundary checks live here, independent of the editor UI.
export function createAdapter(loaders = {}) {
    let api;
    let pending;
    let backend = '';
    let notice = '';
    let lastError = '';
    const loadJs = loaders.js || (() => import('./js/openccjava.js'));
    const loadWasm = loaders.wasm || (async () => {
        if (!globalThis.TeaVM?.wasmGC) {
            await new Promise((resolve, reject) => {
                const script = document.createElement('script');
                script.src = './wasm-gc/openccjava.wasm-runtime.js';
                script.onload = resolve;
                script.onerror = () => { script.remove(); reject(new Error('Could not load Wasm GC runtime')); };
                document.head.append(script);
            });
        }
        return (await globalThis.TeaVM.wasmGC.load('./wasm-gc/openccjava.wasm')).exports;
    });

    function call(method, ...args) {
        lastError = '';
        try {
            if (!api) throw new Error('Engine is not initialized');
            const result = api[method](...args);
            if ((['applyCustomDictionaries', 'resetDictionaries'].includes(method) && result === false) || result === null || (method === 'zhoCheck' && result === -1)) {
                throw new Error(api.getLastError() || `${method} failed`);
            }
            return result;
        } catch (error) {
            lastError = String(error);
            throw error;
        }
    }

    return {
        initialize(requested = new URLSearchParams(location.search).get('backend') || 'wasm') {
            if (pending) return pending;
            pending = (async () => {
                if (!['js', 'wasm'].includes(requested)) throw new Error(`Unknown backend: ${requested}`);
                const candidates = requested === 'wasm' ? ['wasm', 'js'] : ['js'];
                for (const candidate of candidates) {
                    try {
                        const loaded = await (candidate === 'wasm' ? loadWasm() : loadJs());
                        if (!loaded.initialize()) throw new Error(loaded.getLastError());
                        api = loaded;
                        backend = candidate;
                        lastError = '';
                        return;
                    } catch (error) {
                        if (candidate === 'js') throw error;
                        notice = `Wasm GC failed (${String(error)}). Using JavaScript fallback.`;
                    }
                }
            })().catch(error => {
                lastError = String(error);
                pending = undefined;
                throw error;
            });
            return pending;
        },
        convert: (text, config, punctuation = false) => call('convert', text, config, punctuation),
        convertOfficeBytes(input, format, config, punctuation = false, keepFont = true) {
            if (input instanceof ArrayBuffer) input = new Uint8Array(input);
            if (!(input instanceof Uint8Array)) {
                lastError = 'Document input must be Uint8Array or ArrayBuffer';
                throw new TypeError(lastError);
            }
            // JSO bulk marshals byte[]; Wasm GC arrays are never assumed to be JS buffers.
            const signed = new Int8Array(input.buffer, input.byteOffset, input.byteLength);
            const output = call('convertOfficeBytes', signed, format, config, punctuation, keepFont);
            return new Uint8Array(output.buffer, output.byteOffset, output.byteLength).slice();
        },
        getSupportedDictionarySlots: () => Array.from(call('getSupportedDictionarySlots')),
        getDictionaryStatus: () => call('getDictionaryStatus'),
        applyCustomDictionaries(specs, config) {
            return call('applyCustomDictionaries', specs.map(s => s.slot), specs.map(s => s.mode),
                specs.map(s => s.text), config);
        },
        resetDictionaries: config => call('resetDictionaries', config),
        getVersion: () => call('getVersion'),
        getSupportedConfigs: () => Array.from(call('getSupportedConfigs')),
        isValidConfig: config => call('isValidConfig', config),
        normalizeCompatExtended: text => call('normalizeCompatExtended', text),
        detofu: text => call('detofu', text),
        zhoCheck: text => call('zhoCheck', text),
        getLastError: () => lastError || api?.getLastError() || '',
        getBackend: () => backend,
        getBackendNotice: () => notice
    };
}

export default createAdapter();
