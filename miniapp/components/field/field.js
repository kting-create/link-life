Component({
  properties: {
    label: { type: String, value: '' },
    value: { type: String, value: '' },
    placeholder: { type: String, value: '' },
  },
  methods: {
    onInput(e) {
      this.triggerEvent('input', { value: e.detail.value });
    },
  },
});
