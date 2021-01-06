//


Vue.component('line-chart', {
  extends: VueChartJs.Line,
  props: {
    series: {
      type: Object,
      default: null
    },
  },
  mounted() {
    this.gradient = this.$refs.canvas.getContext('2d').createLinearGradient(0, 0, 0, 450)
    this.gradient2 = this.$refs.canvas.getContext('2d').createLinearGradient(0, 0, 0, 450)

    this.gradient2.addColorStop(0, 'rgba(83,154,168, 0.9)')
    this.gradient2.addColorStop(0.5, 'rgba(83,154,168, 0.5)');
    this.gradient2.addColorStop(1, 'rgba(83,154,168, 0.2)');

    this.gradient.addColorStop(0, 'rgba(247,108,6, 0.9)')
    this.gradient.addColorStop(0.5, 'rgba(247,108,6, 0.25)');
    this.gradient.addColorStop(1, 'rgba(247,108,6, 0)');

    zoptions = {
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

    for (i of [0, 1, 2, 3]) {
      // chartdata.datasets[i].label = this.series.columns[i + 1]
      data = [];
      for (value of this.series.values) {
        data.push(value[i + 1]);
      }
      chartdata.datasets[i].data = data;
    }


    labels = [];
    for (value of this.series.values) {
      // var d = moment(value[0]);d.format('H:mm')
      labels.push(value[0]);
    }
    chartdata.labels = labels;

    this.renderChart(chartdata, zoptions)
  }

})

function deviceSelected() {
  console.log(this.selected);
  for (device of this.devices) {
    if (device.id = this.selected) {
      console.log(device);
      this.config = device.config2;
      this.state = device.state2;
      this.series = device.series;
    }
  }
}

var app = new Vue({
  el: '#contents',
  data: {
    devices: '',
    selected: '',
    config: {},
    state: {},
    errors: [],
    series: {},
    sliderOptions: {
      max: 120,
    },
  },
  mounted: function () {
    axios
      .get('/device')
      .then(response => {
    	  this.devices = response.data;  
    	  if (this.devices.length == 1) {
    		  this.selected = this.devices[0].id;
    		  this.deviceSelected();
    	  }
      })
      .catch(error => console.error(error))
  },
  computed: {
    deviceUrl: function () {
      return '/device/' + this.selected;
    }
  },
  methods: {
    deviceSelected: deviceSelected,
    checkForm: function (event) {
      this.errors = [];
      if (!this.selected) {
        this.errors.push('Selected required.');
      }
      // TODO validate all fields
      if (this.config.mode == -2) {
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

      if (this.errors.length) {
        event.preventDefault();
        return false;
      }
      $('#submit').prop('disabled', true);
      axios.put(this.deviceUrl, this.config)
        .then(function (response) {
          $('#submit').prop('disabled', false);
          console.log(response);
        })
        .catch(function (error) {
          $('#submit').prop('disabled', false);
          console.error(error);
        });
      event.preventDefault();
      return true;

    },
  },
  components: {
    'vueSlider': window['vue-slider-component'],
  }
})