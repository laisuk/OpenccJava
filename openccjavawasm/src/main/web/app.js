import ccAdapter from "./openccJavaAdapter.js";
import {EditorState} from "https://esm.sh/@codemirror/state";
import {EditorView, keymap, lineNumbers} from "https://esm.sh/@codemirror/view";
import {defaultKeymap, history, historyKeymap} from "https://esm.sh/@codemirror/commands";
import {detectCjkEncoding} from "./cjkEncodingDetector.js";

const configInput = document.getElementById("config");
const configDescription = document.getElementById("configDescription");
const officeDrop = document.getElementById("officeDrop");
const officeFile = document.getElementById("officeFile");
const officeResult = document.getElementById("officeResult");
const officeResultText = document.getElementById("officeResultText");
const officeDownloadBtn = document.getElementById("officeDownloadBtn");
const openedFileName = document.getElementById("openedFileName");
const downloadOutputBtn = document.getElementById("downloadOutputBtn");
const outputDownloadLink = document.getElementById("outputDownloadLink");
const out = document.getElementById("out");

const configDescriptions = {
    s2t: "Simplified Chinese → Traditional Chinese",
    t2s: "Traditional Chinese → Simplified Chinese",

    s2tw: "Simplified Chinese → Traditional Chinese (Taiwan)",
    s2twp: "Simplified Chinese → Traditional Chinese (Taiwan, phrases)",
    tw2s: "Traditional Chinese (Taiwan) → Simplified Chinese",
    tw2sp: "Traditional Chinese (Taiwan, phrases) → Simplified Chinese",

    s2hk: "Simplified Chinese → Traditional Chinese (Hong Kong)",
    s2hkp: "Simplified Chinese → Traditional Chinese (Hong Kong, phrases)",
    hk2s: "Traditional Chinese (Hong Kong) → Simplified Chinese",
    hk2sp: "Traditional Chinese (Hong Kong, phrases) → Simplified Chinese",

    t2tw: "Traditional Chinese → Traditional Chinese (Taiwan)",
    t2twp: "Traditional Chinese → Traditional Chinese (Taiwan, phrases)",
    tw2t: "Traditional Chinese (Taiwan) → Traditional Chinese",
    tw2tp: "Traditional Chinese (Taiwan, phrases) → Traditional Chinese",

    t2hk: "Traditional Chinese → Traditional Chinese (Hong Kong)",
    t2hkp: "Traditional Chinese → Traditional Chinese (Hong Kong, phrases)",
    hk2t: "Traditional Chinese (Hong Kong) → Traditional Chinese",
    hk2tp: "Traditional Chinese (Hong Kong, phrases) → Traditional Chinese",

    t2jp: "Traditional Chinese → Japanese Shinjitai",
    jp2t: "Japanese Shinjitai → Traditional Chinese",
    s2seal: "Simplified Chinese → Small Seal Script",
    t2seal: "Traditional Chinese → Small Seal Script",
    seal2s: "Small Seal Script → Simplified Chinese",
    seal2t: "Small Seal Script → Traditional Chinese"
};

function updateConfigDescription() {
    configDescription.textContent =
        configDescriptions[configInput.value] ?? "";
}

let cc = null;
let officeDownloadUrl = null;
let officeDownloadName = "";
let officeBusy = false;
let outputDownloadUrl = null;

let openedTextFileName = "";
let openedTextFileBytes = null;
let preserveOpenedTextFile = false;

function clearOpenedTextFile() {
    openedTextFileName = "";
    openedTextFileBytes = null;
    openedFileName.textContent = "";
}

const editorLeft = createEditor(
    document.getElementById("editor-left"),
    "汉字转换测试",
    false,
    () => {
        if (!preserveOpenedTextFile) {
            clearOpenedTextFile();
        }
    }
);

const editorRight = createEditor(
    document.getElementById("editor-right"),
    "",
    true
);

document.getElementById("deTofuBtn").disabled = true;
document.getElementById("normalizeBtn").disabled = true;
document.getElementById("gbMojibakeBtn").disabled = true;
document.getElementById("btn").disabled = true;

try {
    out.textContent = "Loading engine...";

    await ccAdapter.initialize();

    cc = ccAdapter;

    const configs = cc.getSupportedConfigs();

    for (const config of configs) {
        const option = document.createElement("option");
        option.value = config;
        option.textContent = config;

        if (config === "s2t") {
            option.selected = true;
        }

        configInput.appendChild(option);
    }

    updateConfigDescription();
    document.getElementById("supported").textContent = configs.join(", ");

    document.getElementById("normalizeBtn").disabled = false;
    document.getElementById("gbMojibakeBtn").disabled = false;
    document.getElementById("btn").disabled = false;
    officeDrop.classList.remove("disabled");
    officeDrop.setAttribute("aria-disabled", "false");

    document.getElementById("deTofuBtn").disabled = false;
    document.getElementById("backendStatus").textContent =
        `Backend: ${cc.getBackend() === 'wasm' ? 'WebAssembly GC' : 'JavaScript'}. ${cc.getBackendNotice()}`;
    out.textContent = `OpenccJava loaded. (${configs.length} configs available)`;
    document.documentElement.dataset.ready = 'true';
} catch (err) {
    out.textContent = `Failed to load OpenccJava: ${err}`;
    document.documentElement.dataset.ready = 'failed';
    console.error(err);
}

function createEditor(parent, text, readOnly, onDocChanged = null) {
    const extensions = [
        lineNumbers(),
        history(),
        keymap.of([
            ...defaultKeymap,
            ...historyKeymap
        ]),
        EditorView.updateListener.of(update => {
            if (update.docChanged) updateCounts();
        }),
        EditorView.lineWrapping,
        EditorView.editable.of(!readOnly),
        EditorState.readOnly.of(readOnly)
    ];

    if (onDocChanged) {
        extensions.push(
            EditorView.updateListener.of(update => {
                if (update.docChanged) {
                    onDocChanged();
                }
            })
        );
    }

    return new EditorView({
        parent,
        state: EditorState.create({
            doc: text,
            extensions
        })
    });
}

function getEditorText(editor) {
    return editor.state.doc.toString();
}

function setEditorText(editor, text) {
    editor.dispatch({
        changes: {
            from: 0,
            to: editor.state.doc.length,
            insert: text
        }
    });
}

function setInputEditorText(text, preserveFileBytes = false) {
    preserveOpenedTextFile = preserveFileBytes;

    try {
        setEditorText(editorLeft, text);
    } finally {
        preserveOpenedTextFile = false;
    }
}

const textFile = document.getElementById("textFile");
const textEncoding = document.getElementById("textEncoding");

function decodeTextBytes(bytes, encoding) {
    return new TextDecoder(encoding, {fatal: false}).decode(bytes);
}

function refreshOpenedTextFile() {
    if (!openedTextFileBytes) {
        return;
    }

    try {
        const text = decodeTextBytes(openedTextFileBytes, textEncoding.value);
        setInputEditorText(text, true);
        editorLeft.focus();

        out.textContent =
            `Opened ${openedTextFileName} as ${textEncoding.options[textEncoding.selectedIndex].text} ` +
            `(${Array.from(text).length.toLocaleString()} characters).`;
    } catch (err) {
        out.textContent =
            `Failed to decode file as ${textEncoding.value}: ${err instanceof Error ? err.message : err}`;
        console.error(err);
    }
}

document.getElementById("openFileBtn").onclick = () => {
    textFile.click();
};

textFile.addEventListener("change", async () => {
    const file = textFile.files?.[0];
    if (!file) {
        return;
    }

    try {
        openedTextFileName = file.name;
        openedTextFileBytes = new Uint8Array(await file.arrayBuffer());

        const detected = detectCjkEncoding(openedTextFileBytes);
        if (detected.detected) {
            textEncoding.value = detected.encoding;
        }

        openedFileName.textContent = file.name;
        refreshOpenedTextFile();
    } catch (err) {
        clearOpenedTextFile();
        out.textContent =
            `Failed to open file: ${err instanceof Error ? err.message : err}`;
        console.error(err);
    } finally {
        textFile.value = "";
    }
});

textEncoding.addEventListener("change", refreshOpenedTextFile);

function repairGbMojibake(text) {
    const normalized = text.normalize("NFC");

    const cp1252 = new Map([
        [0x20AC, 0x80], [0x201A, 0x82], [0x0192, 0x83], [0x201E, 0x84],
        [0x2026, 0x85], [0x2020, 0x86], [0x2021, 0x87], [0x02C6, 0x88],
        [0x2030, 0x89], [0x0160, 0x8A], [0x2039, 0x8B], [0x0152, 0x8C],
        [0x017D, 0x8E], [0x2018, 0x91], [0x2019, 0x92], [0x201C, 0x93],
        [0x201D, 0x94], [0x2022, 0x95], [0x2013, 0x96], [0x2014, 0x97],
        [0x02DC, 0x98], [0x2122, 0x99], [0x0161, 0x9A], [0x203A, 0x9B],
        [0x0153, 0x9C], [0x017E, 0x9E], [0x0178, 0x9F]
    ]);

    const bytes = Uint8Array.from([...normalized], ch => {
        const cp = ch.codePointAt(0);

        if (cp <= 0xff)
            return cp;

        const byte = cp1252.get(cp);
        if (byte !== undefined)
            return byte;

        throw new Error(
            `Cannot recover byte from U+${cp.toString(16).toUpperCase()}`
        );
    });

    return new TextDecoder("gb18030", {fatal: true}).decode(bytes);
}

const documentMimeTypes = {
    docx: "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    xlsx: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    pptx: "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    epub: "application/epub+zip",
    odt: "application/vnd.oasis.opendocument.text",
    ods: "application/vnd.oasis.opendocument.spreadsheet",
    odp: "application/vnd.oasis.opendocument.presentation",
    txt: "text/plain;charset=utf-8",
    md: "text/markdown;charset=utf-8"
};

function getOfficeFormat(fileName) {
    const dot = fileName.lastIndexOf(".");
    return dot >= 0 ? fileName.slice(dot + 1).toLowerCase() : "";
}

function convertedFileName(fileName) {
    const dot = fileName.lastIndexOf(".");

    if (dot <= 0) {
        return `${fileName}_converted`;
    }

    return `${fileName.slice(0, dot)}_converted${fileName.slice(dot)}`;
}


function clearOutputDownload() {
    if (outputDownloadUrl) {
        URL.revokeObjectURL(outputDownloadUrl);
    }

    outputDownloadUrl = null;
    outputDownloadLink.removeAttribute("href");
    outputDownloadLink.removeAttribute("download");
    outputDownloadLink.textContent = "";
    outputDownloadLink.hidden = true;
}

function prepareOutputDownload() {
    clearOutputDownload();

    const text = getEditorText(editorRight);
    if (!text) {
        out.textContent = "Download: output is empty.";
        return;
    }

    const blob = new Blob([text], {
        type: "text/plain;charset=utf-8"
    });

    const fileName = "opencc_output.txt";
    outputDownloadUrl = URL.createObjectURL(blob);
    outputDownloadLink.href = outputDownloadUrl;
    outputDownloadLink.download = fileName;
    outputDownloadLink.textContent = fileName;
    outputDownloadLink.hidden = false;

    out.textContent = `Output ready for download: ${fileName}`;
}

function clearOfficeResult() {
    if (officeDownloadUrl) {
        URL.revokeObjectURL(officeDownloadUrl);
    }

    officeDownloadUrl = null;
    officeDownloadName = "";
    delete officeDownloadBtn.dataset.url;
    delete officeDownloadBtn.dataset.filename;
    officeResultText.textContent = "";
    officeResult.hidden = true;
}

async function convertOfficeFile(file) {
    if (officeBusy) return;
    if (!cc) {
        out.textContent = "Engine is not loaded yet.";
        return;
    }

    clearOfficeResult();

    const format = getOfficeFormat(file.name);

    if (!Object.hasOwn(documentMimeTypes, format)) {
        out.textContent = `Unsupported document format: ${format || 'unknown'}`;
        return;
    }

    const config = configInput.value;
    const punctuation = document.getElementById('punctuation').checked;
    const keepFont = document.getElementById('keepFont').checked;
    const controls = [configInput, officeFile, officeDownloadBtn, document.getElementById('punctuation'),
        document.getElementById('keepFont'), document.getElementById('btn')];
    const previousDisabled = controls.map(control => control.disabled);
    officeBusy = true;
    controls.forEach(control => { control.disabled = true; });
    officeDrop.classList.add('disabled');
    officeDrop.setAttribute('aria-disabled', 'true');
    officeDrop.setAttribute('aria-busy', 'true');
    try {
        out.textContent = `Converting ${file.name} (${config})… This synchronous operation may pause the interface.`;

        // Two frames give the busy state a rendering opportunity; conversion still blocks.
        await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)));

        if (format !== 'txt' && format !== 'md' && file.size > 64 * 1024 * 1024) {
            throw new Error('File exceeds the 64 MiB browser limit');
        }
        const inputBytes = new Uint8Array(await file.arrayBuffer());
        const startTime = performance.now();

        let outputBytes;
        if (format === 'txt' || format === 'md') {
            const text = decodeTextBytes(inputBytes, textEncoding.value);
            const converted = cc.convert(text, config, punctuation);
            outputBytes = new TextEncoder().encode(converted);
        } else {
            outputBytes = cc.convertOfficeBytes(inputBytes, format, config, punctuation, keepFont);
        }

        const conversionMs = performance.now() - startTime;
        const blob = new Blob([outputBytes], {
            type: documentMimeTypes[format]
        });

        officeDownloadUrl = URL.createObjectURL(blob);
        officeDownloadName = convertedFileName(file.name);
        officeDownloadBtn.dataset.url = officeDownloadUrl;
        officeDownloadBtn.dataset.filename = officeDownloadName;

        officeResultText.textContent = `Ready: ${officeDownloadName} `;
        officeResult.hidden = false;

        out.textContent =
            `Document conversion completed.\n` +
            `Config: ${config}\n` +
            `Punctuation: ${punctuation ? "enabled" : "disabled"}\n` +
            `Keep font: ${keepFont ? "enabled" : "disabled"}\n` +
            `Input: ${file.name}\n` +
            `Output: ${officeDownloadName}\n` +
            `Conversion time: ${conversionMs.toFixed(2)} ms\n` +
            `Click Download converted file when ready.`;
    } catch (err) {
        out.textContent = `Document conversion failed: ${err instanceof Error ? err.message : err}`;
        console.error(err);
    } finally {
        officeFile.value = "";
        officeBusy = false;
        controls.forEach((control, index) => { control.disabled = previousDisabled[index]; });
        officeDrop.classList.remove('disabled');
        officeDrop.setAttribute('aria-disabled', 'false');
        officeDrop.setAttribute('aria-busy', 'false');
    }
}

officeDownloadBtn.addEventListener("click", () => {
    if (!officeDownloadUrl || !officeDownloadName) {
        return;
    }

    const a = document.createElement("a");
    a.href = officeDownloadUrl;
    a.download = officeDownloadName;
    a.click();
});

officeDrop.addEventListener("click", () => officeFile.click());

officeDrop.addEventListener("keydown", event => {
    if (event.key === "Enter" || event.key === " ") {
        event.preventDefault();
        officeFile.click();
    }
});

officeDrop.addEventListener("dragover", event => {
    event.preventDefault();
    officeDrop.classList.add("dragover");
});

officeDrop.addEventListener("dragleave", () => {
    officeDrop.classList.remove("dragover");
});

officeDrop.addEventListener("drop", event => {
    event.preventDefault();
    officeDrop.classList.remove("dragover");

    const file = event.dataTransfer?.files?.[0];
    if (file) {
        void convertOfficeFile(file);
    }
});

officeFile.addEventListener("change", () => {
    const file = officeFile.files?.[0];
    if (file) {
        void convertOfficeFile(file);
    }
});

window.addEventListener("beforeunload", () => {
    clearOfficeResult();
    clearOutputDownload();
});

configInput.addEventListener("change", () => {
    if (!cc) {
        out.textContent = "Engine is not loaded yet.";
        return;
    }

    const config = configInput.value.trim();

    if (!cc.isValidConfig(config)) {
        out.textContent = `Invalid config: ${config}`;
        return;
    }

    updateConfigDescription();
    out.textContent = `Current config set to: ${configInput.value}`;
});

document.getElementById("pasteBtn").onclick = async () => {
    try {
        const text = await navigator.clipboard.readText();
        setInputEditorText(text);
        textEncoding.value = "utf-8";
        out.textContent = "Pasted clipboard text into input.";
    } catch (err) {
        out.textContent = "Paste failed. Browser may have blocked clipboard access.";
        console.error(err);
    }
};

document.getElementById("copyBtn").onclick = async () => {
    try {
        const text = getEditorText(editorRight);
        await navigator.clipboard.writeText(text);
        out.textContent = "Copied output to clipboard.";
    } catch (err) {
        out.textContent = "Copy failed. Browser may have blocked clipboard access.";
        console.error(err);
    }
};

document.getElementById("normalizeBtn").onclick = () => {
    if (!cc) {
        out.textContent = "Engine is not loaded yet.";
        return;
    }

    const text = getEditorText(editorLeft);

    if (!text) {
        out.textContent = "Normalize: input is empty.";
        return;
    }

    const normalized = cc.normalizeCompatExtended(text);

    setInputEditorText(normalized);
    editorLeft.focus();

    out.textContent = "Input normalized with extended Unicode compatibility normalization.";
};

document.getElementById("gbMojibakeBtn").onclick = () => {
    const text = getEditorText(editorLeft);

    try {
        const repaired = repairGbMojibake(text);

        if (repaired === text) {
            out.textContent = "GB Mojibake: nothing to repair.";
            return;
        }

        setInputEditorText(repaired);
        editorLeft.focus();

        out.textContent = "Input repaired from GB Mojibake.";
    } catch {
        out.textContent = "GB Mojibake: nothing to repair.";
    }
};

document.getElementById("clearInputBtn").onclick = () => {
    setInputEditorText("");
    textEncoding.value = "utf-8";
    // editorLeft.focus();
    out.textContent = "Input cleared.";
};

document.getElementById("deTofuBtn").onclick = () => {
    if (!cc) {
        out.textContent = "Engine is not loaded yet.";
        return;
    }

    const text = getEditorText(editorRight);

    if (!text) {
        out.textContent = "DeTofu: output is empty.";
        return;
    }

    const detofued = cc.detofu(text);

    setEditorText(editorRight, detofued);
    clearOutputDownload();
    editorRight.focus();

    out.textContent = "Output processed with DeTofu.";
};

document.getElementById("clearOutputBtn").onclick = () => {
    setEditorText(editorRight, "");
    clearOutputDownload();
    out.textContent = "Output cleared.";
};

downloadOutputBtn.onclick = prepareOutputDownload;

document.getElementById("btn").onclick = () => {
    if (!cc) {
        out.textContent = "Engine is not loaded yet.";
        return;
    }

    const text = getEditorText(editorLeft);

    if (!text) {
        out.textContent = "Convert: input is empty.";
        return;
    }

    const punctuation =
        document.getElementById("punctuation").checked;

    // Measure only the OpenccJava conversion itself.
    // CodeMirror updates, character counting, and zhoCheck are excluded.
    const startTime = performance.now();
    const converted = cc.convert(text, configInput.value, punctuation);
    const conversionMs = performance.now() - startTime;

    const inputCharCount = Array.from(text).length;
    const outputCharCount = Array.from(converted).length;

    const check =
        cc.zhoCheck(converted);

    const language =
        check === 1 ? "Traditional Chinese" :
            check === 2 ? "Simplified Chinese" :
                "Other";

    const charsPerSecond =
        conversionMs > 0 ? inputCharCount / (conversionMs / 1000) : 0;

    out.textContent =
        `Config: ${configInput.value}\n` +
        `Punctuation: ${punctuation ? "enabled" : "disabled"}\n` +
        `Characters processed: ${inputCharCount.toLocaleString()}\n` +
        `Output characters: ${outputCharCount.toLocaleString()}\n` +
        `Conversion time: ${conversionMs.toFixed(2)} ms\n` +
        `Throughput: ${(charsPerSecond / 1_000_000).toFixed(2)}M chars/sec\n` +
        `Output language detected: ${language}`;

    // Let the browser paint the status first, then update CodeMirror.
    // This keeps the UI responsive when replacing very large documents.
    requestAnimationFrame(() => {
        setEditorText(editorRight, converted);
        clearOutputDownload();
    });
};

function updateCounts() {
    document.getElementById('inputCount').textContent = Array.from(getEditorText(editorLeft)).length.toLocaleString();
    document.getElementById('outputCount').textContent = Array.from(getEditorText(editorRight)).length.toLocaleString();
}
updateCounts();
// Export UI operations for the local browser integration harness.
export {editorLeft, editorRight, setInputEditorText, getEditorText, setEditorText, repairGbMojibake, convertOfficeFile};
// Engine errors, including Java auxiliary-operation failures, remain visible.
window.addEventListener('error', event => { out.textContent = `Error: ${event.error || event.message}`; });
window.addEventListener('unhandledrejection', event => { out.textContent = `Error: ${event.reason}`; });
