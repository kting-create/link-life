Component({
  data: {
    visible: false,
    text: '',
    type: 'info',
    anim: '',
  },
  lifetimes: {
    detached() {
      clearTimeout(this._enterTimer);
      clearTimeout(this._exitTimer);
    },
  },
  methods: {
    show(text, type) {
      clearTimeout(this._enterTimer);
      clearTimeout(this._exitTimer);
      this.setData({ visible: true, text: String(text == null ? '' : text), type: type || 'info', anim: 't-in' });
      this._enterTimer = setTimeout(() => {
        this.setData({ anim: 't-out' });
        this._exitTimer = setTimeout(() => {
          this.setData({ visible: false, anim: '' });
        }, 200);
      }, 2200);
    },
  },
});
