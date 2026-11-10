// Dependency-free Chrome DevTools runner for local software validation.
// node openccjavawasm/src/test/run-browser.mjs "C:/Program Files/Google/Chrome/Application/chrome.exe"
import {spawn} from 'node:child_process';
import {mkdir, mkdtemp, readFile, writeFile} from 'node:fs/promises';
import path from 'node:path';

const chrome = process.argv[2];
if (!chrome) throw new Error('Pass the Chrome/Chromium executable path');
const base = process.argv[3] || 'http://127.0.0.1:8080/';
const output = path.resolve('openccjavawasm/build/browser-validation');
await mkdir(output, {recursive: true});
const profile = await mkdtemp(path.join(output, 'profile-'));
const browser = spawn(chrome, ['--headless', '--disable-gpu', '--no-first-run',
    '--remote-debugging-port=0', `--user-data-dir=${profile}`, 'about:blank'], {stdio: 'ignore', windowsHide: true});
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
let socket;
try {
    let port;
    for (let i = 0; i < 100; i++) {
        try { port = (await readFile(path.join(profile, 'DevToolsActivePort'), 'utf8')).split('\n')[0]; break; }
        catch { await delay(100); }
    }
    if (!port) throw new Error('Chrome debugging port unavailable');
    const target = await (await fetch(`http://127.0.0.1:${port}/json/new?about:blank`, {method: 'PUT'})).json();
    socket = new WebSocket(target.webSocketDebuggerUrl);
    await new Promise((resolve, reject) => { socket.onopen = resolve; socket.onerror = reject; });
    let sequence = 0;
    const pending = new Map();
    socket.onmessage = event => {
        const message = JSON.parse(event.data);
        if (process.env.EPUB_DIAGNOSTICS && message.method === 'Runtime.consoleAPICalled') {
            console.log(...message.params.args.map(arg => arg.value || arg.description));
        }
        if (message.id) {
            const item = pending.get(message.id);
            pending.delete(message.id);
            if (message.error) item.reject(new Error(JSON.stringify(message.error)));
            else item.resolve(message.result);
        }
    };
    const send = (method, params = {}) => new Promise((resolve, reject) => {
        const id = ++sequence; pending.set(id, {resolve, reject});
        socket.send(JSON.stringify({id, method, params}));
    });
    const evaluate = async expression => (await send('Runtime.evaluate', {expression, returnByValue: true})).result.value;
    await send('Page.enable');
    await send('Runtime.enable');
    await send('Page.bringToFront');
    const pages = process.env.EPUB_DIAGNOSTICS ? process.env.EPUB_DIAGNOSTICS.split(',') : ['zip.html?backend=js', 'zip.html?backend=wasm',
        'entities.html?backend=js', 'entities.html?backend=wasm',
        'office.html?backend=js', 'office.html?backend=wasm',
        'smoke.html?backend=js', 'smoke.html?backend=wasm',
        'integration.html?backend=js', 'integration.html?backend=wasm'];
    for (const page of pages) {
        const downloadPath = path.join(output, 'downloads-' + page.replace('.html?backend=', '-'));
        await mkdir(downloadPath, {recursive: true});
        await send('Page.setDownloadBehavior', {behavior: 'allow', downloadPath});
        await send('Page.navigate', {url: new URL(page, base).href});
        let state;
        for (let i = 0; i < 600; i++) {
            let watchdog;
            try {
                state = await Promise.race([
                    evaluate(`JSON.stringify({done: document.documentElement.dataset[${JSON.stringify(page.startsWith('epub-diagnostics') ? 'hang' : page.split('.')[0])}], status: document.querySelector('#status, #out')?.textContent})`),
                    new Promise((resolve, reject) => { watchdog = setTimeout(() => reject(new Error('Main-thread watchdog: no response for 45 seconds')), 45000); })
                ]);
            } finally { clearTimeout(watchdog); }
            state = JSON.parse(state);
            if (state.done) break;
            await delay(100);
        }
        const name = page.replace('.html?backend=', '-');
        await writeFile(path.join(output, `${name}.html`), await evaluate('document.documentElement.outerHTML'));
        console.log(page, JSON.stringify(state));
        if (state.done !== 'passed') throw new Error('Browser validation failed');
        const packages = await evaluate('window.packageResults || []');
        for (const result of packages) {
            const folder = path.join(output, name);
            await mkdir(folder, {recursive: true});
            if (path.basename(result.name) !== result.name) throw new Error('Unsafe test output filename');
            await writeFile(path.join(folder, result.name), Buffer.from(result.bytes, 'base64'));
        }
    }
    if (process.env.EPUB_DIAGNOSTICS) process.exitCode = 0;
    if (process.env.EPUB_DIAGNOSTICS) {
        await send('Browser.close');
    } else {
    await send('Network.enable');
    await send('Network.setBlockedURLs', {urls: ['*openccjava.wasm']});
    await send('Page.navigate', {url: new URL('?backend=wasm', base).href});
    for (let i = 0; i < 300; i++) {
        if (await evaluate("document.documentElement.dataset.ready === 'true'")) break;
        await delay(100);
    }
    const fallbackStatus = await evaluate("document.getElementById('backendStatus')?.textContent");
    if (!fallbackStatus?.includes('Backend: JavaScript') || !fallbackStatus.includes('fallback')) {
        throw new Error(`Missing real Wasm-failure fallback notice: ${fallbackStatus}`);
    }
    console.log('Blocked Wasm backend asset:', fallbackStatus);
    await send('Network.setBlockedURLs', {urls: []});
    await send('Page.navigate', {url: base});
    for (let i = 0; i < 300; i++) {
        if (await evaluate("document.documentElement.dataset.ready === 'true'")) break;
        await delay(100);
    }
    // Real rendering and pointer/keyboard input, separate from DOM handler tests.
    const requireValue = async (expression, message) => {
        if (!await evaluate(expression)) throw new Error(message + ': ' + await evaluate("JSON.stringify({active:document.activeElement?.outerHTML, value:document.querySelector('.dictionary-card textarea')?.value})"));
    };
    const chooseTheme = async value => {
        await evaluate(`(() => {const el=document.getElementById('themePreference'); el.value=${JSON.stringify(value)}; el.dispatchEvent(new Event('change'));})()`);
    };
    await chooseTheme('system');
    for (const value of ['dark', 'light']) {
        await send('Emulation.setEmulatedMedia', {features:[{name:'prefers-color-scheme',value}]});
        await delay(100);
        await requireValue(`document.documentElement.dataset.theme === '${value}'`, 'System theme did not follow browser preference');
    }
    await chooseTheme('dark');
    await send('Page.reload');
    for (let i=0;i<300;i++) {
        if (await evaluate("document.documentElement.dataset.ready === 'true'")) break;
        await delay(100);
    }
    await requireValue("document.documentElement.dataset.theme === 'dark' && document.getElementById('themePreference').value === 'dark'", 'Manual theme did not persist');
    await evaluate("document.getElementById('addDictionaryBtn').click()");
    const prepareTab = async () => {
        await evaluate(`(() => {const t=document.querySelector('.dictionary-card textarea'); t.value='abcd'; t.focus(); t.setSelectionRange(1,3); document.querySelector('.dictionary-insert-tab').scrollIntoView({block:'center'});})()`);
        return JSON.parse(await evaluate(`JSON.stringify((() => {const r=document.querySelector('.dictionary-insert-tab').getBoundingClientRect(); return {x:r.x+r.width/2,y:r.y+r.height/2};})())`));
    };
    let point = await prepareTab();
    await send('Input.dispatchMouseEvent', {type:'mousePressed',...point,button:'left',clickCount:1});
    await send('Input.dispatchMouseEvent', {type:'mouseReleased',...point,button:'left',clickCount:1});
    await requireValue("document.querySelector('.dictionary-card textarea').value === 'a\\td'", 'Mouse TAB insertion failed');
    // A focused button must also activate with keyboard Enter.
    await prepareTab();
    await evaluate("document.querySelector('.dictionary-insert-tab').focus()");
    await send('Input.dispatchKeyEvent', {type:'keyDown',key:'Enter',code:'Enter',windowsVirtualKeyCode:13,text:'\r',unmodifiedText:'\r'});
    await send('Input.dispatchKeyEvent', {type:'keyUp',key:'Enter',code:'Enter',windowsVirtualKeyCode:13});
    await requireValue("document.querySelector('.dictionary-card textarea').value === 'a\\td'", 'Keyboard TAB insertion failed');
    await send('Emulation.setDeviceMetricsOverride', {width: 1280, height: 1000, deviceScaleFactor: 1, mobile: false});
    await evaluate('window.scrollTo(0,0)');
    await delay(200);
    await writeFile(path.join(output, 'desktop.png'), Buffer.from((await send('Page.captureScreenshot')).data, 'base64'));
    await send('Emulation.setDeviceMetricsOverride', {width: 390, height: 844, deviceScaleFactor: 1, mobile: true});
    await send('Emulation.setTouchEmulationEnabled', {enabled:true});
    point = await prepareTab();
    await send('Input.dispatchTouchEvent', {type:'touchStart',touchPoints:[point]});
    await send('Input.dispatchTouchEvent', {type:'touchEnd',touchPoints:[]});
    await delay(100);
    await requireValue("document.querySelector('.dictionary-card textarea').value === 'a\\td'", 'Emulated touch TAB insertion failed');
    await requireValue('document.documentElement.scrollWidth <= innerWidth', 'Mobile layout overflows horizontally');
    await chooseTheme('light');
    await evaluate('window.scrollTo(0,0)');
    await delay(200);
    await writeFile(path.join(output, 'mobile.png'), Buffer.from((await send('Page.captureScreenshot')).data, 'base64'));
    console.log('System theme, persisted override, mouse/keyboard/emulated-touch TAB insertion and mobile width checks passed. Desktop dark and mobile light screenshots saved.');
    }
} finally {
    socket?.close();
    browser.kill();
}
