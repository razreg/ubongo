function MachinesController($scope, $http, $interval) {

  var GOOD_STYLE = {paddingTop: '4px', color: 'green'};
  var BAD_STYLE = {paddingTop: '4px', color: 'red'};

  fetchMachines();
  $interval(fetchMachines, 1000 * 60); // refresh machines every minute
  $scope.machines = [];
  $scope.err = {
    display: false,
    msg: '',
    style: {color: 'black'}
  };
  $scope.currMachine = {id: -1};
  $scope.server = {
    lastHeartbeat: '-'
  };

  function fetchMachines() {
    $http.get('rest/api/machines')
      .success(function(data, status, headers, config) {
        for (var i = 0; i < data.length; ++i) {
          if (data[i].lastHeartbeat <= 0) {
            data[i].lastHeartbeat = '-';
            data[i].connected = false;
          } else {
            var date = new Date(0);
            var utcSeconds = data[i].lastHeartbeat / 1000;
            date.setUTCSeconds(utcSeconds);
            data[i].lastHeartbeat = dateInNiceFormat(date);
            data[i].connected = !isDateTooOld(utcSeconds);
          }
        }
        $scope.machines = data;
        $scope.machineGridOptions.data = $scope.machines;
      })
      .error(function(data, status, headers, config) {
        displayMsg(true, 'Failed to load machines', BAD_STYLE);
      });
  }

  function isDateTooOld(utcSeconds) {
    return new Date().getUTCSeconds() - utcSeconds > 60 * 3; // 3 minutes interval to detect disconnected machine
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
      {name: 'lastHeartbeat', displayName: "Last Heartbeat", visible: false}
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

  function displayMsg(disp, msg, style) {
    $scope.err.display = disp;
    $scope.err.msg = msg;
    $scope.err.style = style;
  }

}