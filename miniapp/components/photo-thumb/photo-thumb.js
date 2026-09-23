Component({
  properties: {
    src: { type: String, value: '' },
    width: { type: Number, value: 144 },
  },
  methods: {
    onOpen() {
      this.triggerEvent('open');
    },
  },
});
