//

//Vue.use(VueMaterial.default);

Vue.component('line-chart', {
  extends: VueChartJs.Line,
  props: {
    series: {
      type: Object,
      default: null
    },
  },
  mounted() {
    this.renderLineChart();
  },
  computed: {
    chartData: function() {
      return this.series;
    }
  },
  watch: {
    series: function() {
      //this._chart.destroy();
      //this.renderChart(this.data, this.options);
      this.renderLineChart();
    }
  },
  methods: {
    renderLineChart: function () {
      this.gradient = this.$refs.canvas.getContext('2d').createLinearGradient(0, 0, 0, 450)
      this.gradient2 = this.$refs.canvas.getContext('2d').createLinearGradient(0, 0, 0, 450)

      this.gradient2.addColorStop(0, 'rgba(83,154,168, 0.9)')
      this.gradient2.addColorStop(0.5, 'rgba(83,154,168, 0.5)');
      this.gradient2.addColorStop(1, 'rgba(83,154,168, 0.2)');

      this.gradient.addColorStop(0, 'rgba(247,108,6, 0.9)')
      this.gradient.addColorStop(0.5, 'rgba(247,108,6, 0.25)');
      this.gradient.addColorStop(1, 'rgba(247,108,6, 0)');

      options = {
        responsive: true,
        maintainAspectRatio: false,
        aspectRatio: 0.5,
        scales: {
          xAxes: [{
            type: 'time',
            time: {
              unit: 'minute'
            }
          }]
        },
      };

      chartdata = {
        labels: [],
        datasets: [
          {
            label: 'Temperature',
            backgroundColor: 'rgba(0, 240, 0, 0.1)',
            borderColor: '#0E0',
            borderWidth: 2,
            pointRadius: 0,
            lineTension: 0.1,
            data: []
          }, {
            label: 'Power',
            borderColor: '#EE0',
            borderWidth: 0.1,
            pointRadius: 0,
            backgroundColor: 'rgba(240, 128, 0, 0.1)',
            lineTension: 0,
            data: []
          }, {
            label: 'Min',
            borderColor: "#FC2525",
            borderWidth: 1,
            pointRadius: 0,
            backgroundColor: 'rgba(0, 0, 0, 0.0)',
            lineTension: 0,
            data: []
          }, {
            label: 'Max',
            borderColor: '#E11',
            pointRadius: 0,
            borderWidth: 1,
            backgroundColor: 'rgba(0, 0, 0, 0.0)',
            lineTension: 0,
            data: []
          }
        ]
      };

      if (this.chartData && this.chartData.values) {
        for (i of [0, 1, 2, 3]) {
          // chartdata.datasets[i].label = this.chartData.columns[i + 1]
          data = [];
          for (value of this.chartData.values) {
            data.push(value[i + 1]);
          }
          chartdata.datasets[i].data = data;
        }

        labels = [];
        for (value of this.chartData.values) {
          // var d = moment(value[0]);d.format('H:mm')
          labels.push(value[0]);
        }
        chartdata.labels = labels;

        this.renderChart(chartdata, options)
      }
    }
  }
})

var app = new Vue({
  el: '#contents',
  vuetify: new Vuetify(),
  data: {
    user: {},
    devices: [],
    selected: '',
    config: {},
    state: {},
    errors: [],
    series: {},
    minmax: [],
    valid: true,
    //sliderOptions: {
    //  max: 120,
    //  contained: true,
    //},
    timer: '',
  },
  mounted: function () {
    this.fetchUser();
    this.timer = setInterval(this.refreshDevice, 60000)
    document.addEventListener('visibilitychange', this.visibilityChange);
  },
  computed: {
    deviceUrl: function () {
      return '/device/' + this.selected;
    },
  },
  methods: {
    visibilityChange: function () {
      doVisualUpdates = !document.hidden;
      if (document.hidden) {
        clearInterval(this.timer);
      } else {
        clearInterval(this.timer);
        this.refreshDevice();
        this.timer = setInterval(this.refreshDevice, 60000)
      }
    },
    selectDevice: function(device) {
      console.log("Select device", device);
      if (device) {
        this.config = device.config2;
        this.state = device.state2;
        this.state.updateTime = device.state.updateTime;
        this.series = device.series;
        this.minmax = [device.config2.min, device.config2.max]
      } else {
        this.config = {};
        this.state = {};
        this.series = [];
        this.minmax = [];
      }
    },
    refreshDevice: function () {
      if (this.selected) {
        axios
        .get(this.deviceUrl)
        .then(response => {
          this.selectDevice(response.data);
        })
        .catch(error => {
          console.error(error);
          if (error.response && error.response.status == 401) {
            clearInterval(this.timer);
            this.fetchUser();
          }
        })
      }
    },
    fetchDevices: function () {
      axios
      .get('/device')
      .then(response => {
        this.devices = response.data;
        if (this.devices.length == 1) {
          this.selected = this.devices[0].id;
          this.deviceSelected();
        }
      })
      .catch(error => {
        console.error(error);
      })
    },
    fetchUser () {
      axios.defaults.headers.post['X-XSRF-TOKEN'] = Cookies.get('XSRF-TOKEN');
      axios.defaults.headers.put['X-XSRF-TOKEN'] = Cookies.get('XSRF-TOKEN');
      axios.defaults.headers.delete['X-XSRF-TOKEN'] = Cookies.get('XSRF-TOKEN');
      axios
        .get('/user')
        .then(response => {
          this.user = response.data;
          this.fetchDevices();
        })
        .catch(error => {
          if (error.response.status == 401) {
            console.log("Not logged in");
            this.$set(this.user, 'error', 'No access');
            this.$set(this.user, 'login', '');
            this.$set(this.user, 'name', '');
            this.selectDevice(null);
            this.selected = '';
            this.devices = [];
          } else {
            console.error(error);
          }
        });
    },
    deviceSelected: function () {
      console.log(this.selected);
      for (device of this.devices) {
        if (device.id = this.selected) {
          this.selectDevice(device);
        }
      }
    },
    logout: function () {
      axios
        .post('/logout')
        .then(response => {
          this.user = {};
          this.config = {};
          this.state = {};
          this.devices = [];
          this.selected = '';
        })
        .catch(error => {
          console.error(error);
          this.user = {}; // TODO not always
          this.config= {};
          this.state = {};
          this.devices = [];
          this.selected = '';
        }
        );
      return true;
    },
    validate: function (event) {
      this.errors = [];
      if (!this.selected) {
        this.errors.push('Selected required.');
      }
      // TODO validate all fields
      if (this.config.mode == -2) {
        // FIXME not in validation
        this.config.min = this.minmax[0];
        this.config.max = this.minmax[1];
        if (!this.config.min) {
          this.errors.push('Min required.');
        }
        if (!this.config.max) {
          this.errors.push('Min required.');
        }
      } else if (this.config.mode == -1) {
        if (!this.config.onMins) {
          this.errors.push('onMins required.');
        }
        if (!this.config.offMins) {
          this.errors.push('offMins required.');
        }
      } else if (this.config.mode == 0) {
      } else if (this.config.mode == 100) {
      } else {
        this.errors.push('Mode required.');
      }
      this.valid = (this.errors.length == 0)
    },
    submitForm: function (event) {
      this.validate();
      if (!this.valid) {
        event.preventDefault();
        return false;
      }
      //$('#submit').prop('disabled', true);
      axios.put(this.deviceUrl, this.config)
        .then(function (response) {
          //$('#submit').prop('disabled', false);
          console.log(response);
        })
        .catch(function (error) {
          //$('#submit').prop('disabled', false);
          console.error(error);
        });
      event.preventDefault();
      return true;
    },
  },
  components: {
    //'vueSlider': window['vue-slider-component'],
  },
  beforeDestroy () {
    clearInterval(this.timer)
  }
})