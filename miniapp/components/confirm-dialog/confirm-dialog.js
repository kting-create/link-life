Component({
  data: {
    open: false,
    title: '',
    message: '',
    danger: false,
    confirmText: '确认',
  },
  methods: {
    show(opts) {
      const o = opts || {};
      return new Promise((resolve) => {
        this._resolver = resolve;
        this.setData({
          open: true,
          title: o.title || '',
          message: o.message || '',
          danger: !!o.danger,
          confirmText: o.confirmText || '确认',
        });
      });
    },
    finish(v) {
      const r = this._resolver;
      this._resolver = null;
      this.setData({ open: false });
      if (r) r(v);
    },
    onMask() {
      this.finish(false);
    },
    onCancel() {
      this.finish(false);
    },
    onConfirm() {
      this.finish(true);
    },
    noop() {},
  },
});
