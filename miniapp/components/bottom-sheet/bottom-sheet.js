Component({
  properties: {
    show: { type: Boolean, value: false },
  },
  methods: {
    onMask() {
      this.triggerEvent('close');
    },
    noop() {},
  },
});
