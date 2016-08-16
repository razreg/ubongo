function MachinesController($scope, $http, $interval) {

  fetchMachines();
  $interval(fetchMachines, 1000 * 60); // refresh machines every minute
  $scope.machines = [];
  $scope.err = {
    display: false,
    msg: '',
    style: {color: 'black'}
  };
  $scope.currMachine = {id: -1};

  function fetchMachines() {
    $http.get('rest/api/machines')
      .success(function(data, status, headers, config) {
        for (var i = 0; i < data.length; ++i) {
          var date = new Date(0);
          date.setUTCSeconds(data[i].lastUpdated / 1000);
          data[i].lastUpdated = dateInNiceFormat(date);
        }
        $scope.machines = data;
        $scope.machineGridOptions.data = $scope.machines;
      })
      .error(function(data, status, headers, config) {
        // TODO reflect in UI (error message)
      });
  }

  function dateInNiceFormat(date) {
    return date.getFullYear() + '-' + toTwoDigits(date.getMonth()+1) + '-' +
      toTwoDigits(date.getDate()) + ' ' + toTwoDigits(date.getHours()) + ':' +
      toTwoDigits(date.getMinutes()) + ':' + toTwoDigits(date.getSeconds());
  }

  function toTwoDigits(num) {
    return num < 10 ? '0'+num : num+'';
  }

  // machines grid
  var template = '<div class="ui-grid-cell-contents ng-binding ng-scope" ' +
    'ng-style="{backgroundColor: COL_FIELD ? \'#9FE0A9\' : \'#F56767\'}"></div>';
  $scope.machineGridOptions = {
    data: $scope.machines,
    enableSorting: true,
    enableSelectAll: false,
    multiSelect: false,
    enableRowSelection: true,
    noUnselect: false,
    showGridFooter: false,
    enableRowHeaderSelection: true,
    enableSelectionBatchEvent: false,
    enableFullRowSelection: true,
    enableHorizontalScrollbar: true,
    columnDefs: [
      {name: 'id', displayName: "ID", visible: false},
      {name: 'host'},
      {name: 'description'},
      {name: 'connected', cellTemplate: template},
      {name: 'active', cellTemplate: template},
      {name: 'lastUpdated', displayName: "Last Heartbeat", visible: false}
    ]
  };
  $scope.machineGridOptions.onRegisterApi = function(gridApi) {
    //set gridApi on scope
    $scope.gridApi = gridApi;
    gridApi.selection.on.rowSelectionChanged($scope, function(row) {
      $scope.machinesSelected = gridApi.selection.getSelectedRows();
      if ($scope.machinesSelected.length > 0) {
        $scope.currMachine = $scope.machinesSelected[0];
      } else {
        $scope.currMachine = {id: -1};
      }
    });
  };

}