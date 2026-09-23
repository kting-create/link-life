Component({
  properties: {
    name: { type: String, value: '' },
    title: { type: String, value: '' },
  },
  methods: {
    onTap() {
      this.triggerEvent('tap');
    },
  },
});
