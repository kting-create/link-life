Component({
  properties: {
    length: { type: Number, value: 6 },
    value: { type: String, value: '' },
  },
  data: {
    cells: [],
    focusIdx: -1,
  },
  observers: {
    value(v) {
      const next = String(v == null ? '' : v);
      // 位姿守卫：与 web CodeInput `if (next === cells.join('')) return` 同语义，
      // 防止父层回显短串时 split 重建导致已输入格左挤
      if (next === this.data.cells.join('')) return;
      this.setData({ cells: this.toCells(next, this.data.length) });
    },
    length(n) {
      this.setData({ cells: this.toCells(this.data.cells.join(''), n) });
    },
  },
  lifetimes: {
    attached() {
      this.setData({ cells: this.toCells(this.data.value, this.data.length) });
    },
  },
  methods: {
    toCells(str, len) {
      const s = String(str == null ? '' : str).replace(/\D/g, '');
      const n = Number(len) || 6;
      const arr = [];
      for (let i = 0; i < n; i++) arr.push(s[i] || '');
      return arr;
    },
    sync(cells) {
      this.setData({ cells: cells });
      this.triggerEvent('change', { value: cells.join('') });
    },
    onInput(e) {
      const i = e.currentTarget.dataset.i;
      const len = this.data.length;
      const raw = String(e.detail.value || '').replace(/\D/g, '');
      const cells = this.data.cells.slice();
      if (raw.length > 1) {
        // 粘贴/自动填充：与 web onPaste 同语义，整串落格（从首位铺开）
        const next = this.toCells(raw, len);
        this.sync(next);
        const filled = next.join('').length;
        this.setFocus(filled < len ? Math.min(filled, len - 1) : -1);
        return;
      }
      const v = raw.slice(-1);
      cells[i] = v;
      this.sync(cells);
      if (v && i < len - 1) this.setFocus(i + 1);
      else if (!v) this.setFocus(i);
    },
    onFocus(e) {
      this.setData({ focusIdx: e.currentTarget.dataset.i });
    },
    setFocus(idx) {
      this.setData({ focusIdx: -1 }, () => {
        this.setData({ focusIdx: idx });
      });
    },
  },
});
