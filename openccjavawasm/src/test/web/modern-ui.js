// Runs inside the existing real-browser integration suite on each backend.
export async function validateModernUI(adapter, assert, tick, convertOfficeFile, editors) {
    const el = id => document.getElementById(id);
    const {editorLeft, editorRight, setInputEditorText, getEditorText} = editors;
    const theme = el('themePreference');
    const originalTheme = theme.value;
    const historyDoc = getEditorText(editorLeft);
    const selection = editorLeft.state.selection.main;
    for (const value of ['light', 'dark', 'system']) {
        theme.value = value; theme.dispatchEvent(new Event('change'));
        assert(document.documentElement.dataset.theme === (value === 'system' ?
            (matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light') : value), `Theme ${value}`);
        assert(getEditorText(editorLeft) === historyDoc && editorLeft.state.selection.main.eq(selection), 'Theme preserves editor document/selection');
    }
    theme.value = originalTheme; theme.dispatchEvent(new Event('change'));
    const cards = el('dictionaryCards');
    el('addDictionaryBtn').click();
    const first = cards.lastElementChild;
    const textarea = first.querySelector('textarea');
    const tabButton = first.querySelector('.dictionary-insert-tab');
    for (const [start, end, expected] of [[0,0,'\tabcd'],[2,2,'ab\tcd'],[4,4,'abcd\t'],[1,3,'a\td']]) {
        textarea.value = 'abcd'; textarea.focus(); textarea.setSelectionRange(start,end);
        // Keyboard focus on the button must preserve the textarea's saved native selection.
        tabButton.focus(); tabButton.click();
        assert(textarea.value === expected && textarea.selectionStart === start + 1 && textarea.selectionEnd === start + 1, 'Insert TAB at cursor / selected range');
    }
    textarea.value = 'a'; textarea.setSelectionRange(1,1);
    textarea.dispatchEvent(new KeyboardEvent('keydown', {key:'Tab', cancelable:true}));
    assert(textarea.value === 'a\t', 'Desktop TAB insertion');
    textarea.dispatchEvent(new KeyboardEvent('keydown', {key:'Escape'}));
    const leave = new KeyboardEvent('keydown', {key:'Tab',cancelable:true}); textarea.dispatchEvent(leave);
    assert(!leave.defaultPrevented && textarea.value === 'a\t', 'Escape then Tab leaves textarea');
    first.querySelector('select').value = 'STPhrases';
    textarea.value = '# comment\n\n汉字\t自訂 漢字\n转换\t替換\n汉字\t自訂';
    el('config').value = 's2t'; el('punctuation').checked = true;
    const status = () => el('dictionaryStatus').textContent;
    async function operation(id) {
        el(id).click();
        for (let i=0; i<100 && el('dictionaryPanel').getAttribute('aria-busy') === 'true'; i++) await tick();
        assert(el('dictionaryPanel').getAttribute('aria-busy') === 'false', 'Dictionary operation settled');
    }
    assert(status() === 'Default', 'Draft leaves active status unchanged');
    await operation('applyDictionariesBtn');
    assert(status() === 'Custom (1 slot - 2 entries)', 'Applied status counts unique mappings');
    assert(adapter.convert('汉字转换测试','s2t',true) === '自訂替換測試', 'Append output uses first target token');
    setInputEditorText('汉字转换测试'); el('btn').click(); await tick();
    assert(getEditorText(editorRight) === '自訂替換測試' && el('out').textContent.includes('Dictionary: Custom'), 'Editor uses active dictionary and reports it');
    const active = status();
    for (const invalid of ['', '# only comments', '汉字 missing tab', '\t目標', '来源\t', 'valid\tok\nbroken']) {
        textarea.value = invalid;
        await operation('applyDictionariesBtn');
        assert(status() === active && el('out').textContent.includes('failed'), 'Invalid draft preserves active status');
        assert(adapter.convert('汉字','s2t') === '自訂', 'Invalid draft preserves converter');
    }
    textarea.value = '汉字\t新詞';
    el('addDictionaryBtn').click(); const second = cards.lastElementChild;
    second.querySelector('select').value = 'STPhrases'; second.querySelector('textarea').value = '测试\t驗證';
    await operation('applyDictionariesBtn');
    assert(status() === active && el('out').textContent.includes('duplicate slot'), 'Duplicate slots rejected');
    second.querySelector('select').value = 'STCharacters';
    second.querySelector('textarea').value = '测\t驗';
    second.querySelector('textarea').setSelectionRange(0,0);
    second.querySelector('.dictionary-insert-tab').click();
    assert(textarea.value === '汉字\t新詞' && second.querySelector('textarea').value.startsWith('\t'), 'Independent dynamic card TAB buttons');
    second.querySelector('textarea').value = '测\t驗';
    await operation('applyDictionariesBtn');
    assert(status() === 'Custom (2 slots - 2 entries)' && adapter.convert('汉字测','s2t') === '新詞驗', 'Multiple slots use same snapshot');
    // Force a Core preparation failure after the first spec was validated.
    let failed = false;
    try { adapter.applyCustomDictionaries([{slot:'STPhrases',mode:'append',text:'汉字\t坏'},
        {slot:'STCharacters',mode:'invalid',text:'测\t坏'}], 's2t'); } catch { failed = true; }
    assert(failed && adapter.getDictionaryStatus() === status() && adapter.convert('汉字测','s2t') === '新詞驗', 'Bridge failure rolls back atomically');
    second.querySelector('button').click();
    assert(cards.children.length === 1, 'Dynamic card removal');
    first.querySelector('.dictionary-mode').value = 'override';
    textarea.value = '汉字\t自訂';
    await operation('applyDictionariesBtn');
    assert(adapter.convert('汉字头发','s2t') === '自訂頭發', 'Override replaces original phrase slot');
    await operation('applyDictionariesBtn');
    assert(adapter.convert('汉字头发','s2t') === '自訂頭發', 'Repeated Apply starts from original base');
    // Inspect ZIP entries using the independent browser decompressor.
    async function zipText(bytes) {
        const view = new DataView(bytes.buffer,bytes.byteOffset,bytes.byteLength);
        const texts = [];
        for (let i=0;i+46<bytes.length;i++) {
            if (view.getUint32(i,true) !== 0x02014b50) continue;
            const size=view.getUint32(i+20,true), nameLen=view.getUint16(i+28,true),
                local=view.getUint32(i+42,true), method=view.getUint16(i+10,true);
            const name=new TextDecoder().decode(bytes.subarray(i+46,i+46+nameLen));
            const start=local+30+view.getUint16(local+26,true)+view.getUint16(local+28,true);
            if (!/\.(xml|xhtml|html)$/.test(name)) continue;
            const data=bytes.slice(start,start+size);
            const result=method===0 ? data : new Uint8Array(await new Response(new Blob([data]).stream().pipeThrough(new DecompressionStream('deflate-raw'))).arrayBuffer());
            texts.push(new TextDecoder().decode(result));
        }
        return texts.join('\n');
    }
    for (const format of ['docx','xlsx','pptx','odt','ods','odp','epub']) {
        const input=await (await fetch(`./office-fixtures/sample.${format}`)).arrayBuffer();
        const output=adapter.convertOfficeBytes(input,format,'s2t',true,true);
        assert((await zipText(output)).includes('自訂'), `Active dictionary reaches ${format}`);
    }
    first.querySelector('.dictionary-mode').value = 'append';
    textarea.value = '转换\t新換';
    el('applyDictionariesBtn').click();
    assert(el('btn').disabled && el('resetDictionariesBtn').disabled, 'Apply blocks overlapping UI operations');
    await convertOfficeFile(new File(['汉字'],'blocked.txt'));
    assert(el('dictionaryPanel').getAttribute('aria-busy') === 'true', 'Drop conversion cannot interleave dictionary preparation');
    for (let i=0;i<100 && el('dictionaryPanel').getAttribute('aria-busy') === 'true';i++) await tick();
    assert(adapter.convert('汉字转换头发','s2t') === '漢字新換頭髮', 'Append replaces old custom snapshot and restores base mappings');
    assert(el('punctuation').checked, 'Dictionary operations preserve punctuation setting');
    textarea.value = '汉字\t自訂';
    el('config').value = 's2twp'; el('config').dispatchEvent(new Event('change'));
    assert(status().startsWith('Custom'), 'Configuration change preserves active status');
    await operation('resetDictionariesBtn');
    assert(status() === 'Default' && cards.children.length === 1 && textarea.value === '汉字\t自訂' && el('config').value === 's2twp', 'Reset retains drafts/configuration');
    assert(adapter.convert('汉字头发','s2t') === '漢字頭髮', 'Reset restores original base');
    el('config').value='s2t';
    // Detect UTF-16 with BOM for Drop Zone only; manual UTF-8 must reject it strictly.
    const utf16=Uint8Array.from([255,254,73,108,87,91]);
    el('textEncoding').value='utf-8'; el('autoDetectEncoding').checked=true;
    await convertOfficeFile(new File([utf16],'encoding.md'));
    assert(!el('officeResult').hidden && await (await fetch(el('officeDownloadBtn').dataset.url)).text() === '漢字', 'Drop auto-detection overrides manual encoding');
    assert(el('textEncoding').value === 'utf-8', 'Drop detection preserves editor encoding selection');
    el('autoDetectEncoding').checked=false;
    await convertOfficeFile(new File([utf16],'encoding.md'));
    assert(el('officeResult').hidden && el('out').textContent.includes('failed'), 'Manual decoding reports invalid bytes');
    el('textEncoding').value='utf-16le';
    await convertOfficeFile(new File([utf16],'encoding.md'));
    assert(!el('officeResult').hidden && await (await fetch(el('officeDownloadBtn').dataset.url)).text() === '漢字', 'Drop manual encoding respected');
    el('autoDetectEncoding').checked=true; el('textEncoding').value='utf-8';
    await convertOfficeFile(new File([Uint8Array.from([0xff])],'unknown.txt'));
    assert(el('officeResult').hidden && el('out').textContent.includes('failed'), 'Unknown encoding reports failure');
    first.remove();
}
