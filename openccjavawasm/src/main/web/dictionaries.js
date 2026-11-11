// Draft cards never own active converter state. Core owns all dictionary patching.
export function createDictionaryUI(cc, configInput, report, isOfficeBusy) {
    const dictionaryCards = document.getElementById('dictionaryCards');
    const dictionaryStatus = document.getElementById('dictionaryStatus');
    const addDictionaryBtn = document.getElementById('addDictionaryBtn');
    const applyDictionariesBtn = document.getElementById('applyDictionariesBtn');
    const resetDictionariesBtn = document.getElementById('resetDictionariesBtn');
    const availableDictionarySlots = cc.getSupportedDictionarySlots();
    let dictionaryCardId = 0;
    let dictionaryBusy = false;
    function addDictionaryCard() {
        const id = ++dictionaryCardId;
        const card = document.createElement("div");
        card.className = "dictionary-card";
        card.innerHTML = `
            <div class="dictionary-fields">
                <strong>Dictionary ${id}</strong>
                <label for="dictionarySlot${id}">Slot <select id="dictionarySlot${id}" class="dictionary-slot"></select></label>
                <label for="dictionaryMode${id}">Mode <select id="dictionaryMode${id}" class="dictionary-mode"><option value="append">append</option><option value="override">override</option></select></label>
                <button type="button" class="dictionary-remove" aria-label="Remove dictionary ${id}">Remove</button>
            </div>
            <div class="dictionary-entries-heading">
                <label for="dictionaryPairs${id}">Entries</label>
                <button type="button" class="dictionary-insert-tab">Insert Tab at cursor</button>
            </div>
            <textarea id="dictionaryPairs${id}" aria-describedby="dictionaryHelp" spellcheck="false" placeholder="source&#9;target"></textarea>`;
        card.dataset.dictionaryId = String(id);
        const slotSelect = card.querySelector("select");
        for (const slot of availableDictionarySlots) {
            const option = document.createElement("option");
            option.value = option.textContent = slot;
            slotSelect.appendChild(option);
        }
        const used = new Set(Array.from(dictionaryCards.querySelectorAll(".dictionary-slot"), el => el.value));
        slotSelect.value = availableDictionarySlots.find(slot => !used.has(slot)) ?? availableDictionarySlots[0];
        card.querySelector("button").onclick = () => { card.remove(); renumberCards(); };
        const textarea = card.querySelector("textarea");
        const insertTabButton = card.querySelector(".dictionary-insert-tab");
        // Keep the textarea selection when a pointer taps the button, including on mobile.
        insertTabButton.addEventListener("pointerdown", event => event.preventDefault());
        insertTabButton.addEventListener("click", () => {
            const start = textarea.selectionStart;
            const end = textarea.selectionEnd;
            textarea.focus({preventScroll: true});
            textarea.setRangeText("\t", start, end, "end");
        });
        let escapeTab = false;
        textarea.addEventListener("keydown", event => {
            if (event.key === "Tab" && !event.shiftKey && !event.ctrlKey && !event.metaKey && !event.altKey && !escapeTab) {
                event.preventDefault();
                textarea.setRangeText("\t", textarea.selectionStart, textarea.selectionEnd, "end");
            }
            escapeTab = event.key === "Escape";
        });
        dictionaryCards.appendChild(card);
        renumberCards();
        slotSelect.focus();
    }

    function renumberCards() {
        Array.from(dictionaryCards.children).forEach((card, index) => {
            card.dataset.dictionaryId = String(index + 1);
            card.querySelector('strong').textContent = `Dictionary ${index + 1}`;
            card.querySelector('button').setAttribute('aria-label', `Remove dictionary ${index + 1}`);
        });
    }

    function parseDictionaryCards() {
        const seen = new Set();
        const specs = [];
        for (const card of dictionaryCards.children) {
            const prefix = `Dictionary ${card.dataset.dictionaryId}`;
            const slot = card.querySelector(".dictionary-slot").value;
            const mode = card.querySelector(".dictionary-mode").value;
            if (!availableDictionarySlots.includes(slot)) throw new Error(`${prefix}: invalid slot: ${slot}`);
            if (mode !== "append" && mode !== "override") throw new Error(`${prefix}: invalid mode: ${mode}`);
            if (seen.has(slot)) throw new Error(`${prefix}: duplicate slot ${slot}. Use one card per slot.`);
            seen.add(slot);
            // Core owns OpenCC entry parsing, including strict line validation.
            specs.push({slot, mode, text: card.querySelector('textarea').value});
        }
        if (!specs.length) throw new Error("Add a dictionary before applying, or use Reset to Default.");
        return specs;
    }

    async function activate(reset) {
        if (dictionaryBusy || isOfficeBusy()) return;
        dictionaryBusy = true;
        const controls = [...document.querySelectorAll('#dictionaryPanel button, #dictionaryPanel select, #dictionaryPanel textarea'),
            configInput, document.getElementById('btn'), document.getElementById('officeFile')];
        const previous = controls.map(el => el.disabled);
        controls.forEach(el => { el.disabled = true; });
        document.getElementById('dictionaryPanel').setAttribute('aria-busy', 'true');
        try {
            // Capture drafts before yielding so the prepared snapshot is consistent.
            const specs = reset ? null : parseDictionaryCards();
            const config = configInput.value;
            report(reset ? 'Restoring default dictionaries…' : 'Preparing dictionaries… This synchronous operation may pause the interface.');
            await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)));
            if (reset) cc.resetDictionaries(config);
            else cc.applyCustomDictionaries(specs, config);
            dictionaryStatus.textContent = cc.getDictionaryStatus();
            report(`Active dictionary: ${dictionaryStatus.textContent}. Draft cards are retained.`);
        } catch (error) {
            report(`Dictionary operation failed: ${error instanceof Error ? error.message : error}`);
        } finally {
            controls.forEach((el, i) => { el.disabled = previous[i]; });
            document.getElementById('dictionaryPanel').setAttribute('aria-busy', 'false');
            dictionaryBusy = false;
        }
    }
    addDictionaryBtn.onclick = addDictionaryCard;
    applyDictionariesBtn.onclick = () => activate(false);
    resetDictionariesBtn.onclick = () => activate(true);
    [addDictionaryBtn, applyDictionariesBtn, resetDictionariesBtn].forEach(el => { el.disabled = false; });
    dictionaryStatus.textContent = cc.getDictionaryStatus();
    return {isBusy: () => dictionaryBusy};
}
