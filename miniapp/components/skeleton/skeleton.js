const WIDTHS = ['80%', '55%', '65%'];

Component({
  properties: {
    rows: { type: Number, value: 3 },
  },
  data: {
    list: [],
  },
  observers: {
    rows(n) {
      const total = Number(n) || 0;
      const list = [];
      for (let i = 0; i < total; i++) list.push(WIDTHS[i % WIDTHS.length]);
      this.setData({ list });
    },
  },
  lifetimes: {
    attached() {
      const total = Number(this.data.rows) || 0;
      const list = [];
      for (let i = 0; i < total; i++) list.push(WIDTHS[i % WIDTHS.length]);
      this.setData({ list });
    },
  },
});
