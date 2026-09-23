Component({
  properties: {
    items: { type: Array, value: [] },
    current: { type: String, value: '' },
  },
  data: {
    idx: 0,
    count: 1,
  },
  observers: {
    'items, current': function (items, current) {
      const list = Array.isArray(items) ? items : [];
      let i = list.findIndex((it) => it && it.key === current);
      if (i < 0) i = 0;
      this.setData({ idx: i, count: list.length || 1 });
    },
  },
  methods: {
    onTap(e) {
      this.triggerEvent('change', { value: e.currentTarget.dataset.key });
    },
  },
});
