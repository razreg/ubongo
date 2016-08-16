function MachinesController($scope, $http, $interval) {

  $interval(fetchMachines, 1000 * 60); // refresh machines every minute
  $scope.machines = [{id: 2, host: '127.0.0.1', description: 'localhost', connected: true, active: false, lastHeartbeat: 'long time ago'}];
  $scope.err = {
    display: false,
    msg: '',
    style: {color: 'black'}
  };

  function fetchMachines() {
    /* TODO
    $http.get('rest/api/machines')
      .success(function(data, status, headers, config) {
        $scope.machines = data;
      })
      .error(function(data, status, headers, config) {
        // TODO reflect in UI (error message)
      });*/
  }

  // machines grid
  var template = '<div ng-style="{color: COL_FIELD ? \'#29a329\' : \'#ff1a1a\'}" ng-bind="COL_FIELD"></div>';
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
  /* TODO
  $scope.taskGridOptions.onRegisterApi = function(gridApi) {
    //set gridApi on scope
    $scope.gridApi = gridApi;
    gridApi.selection.on.rowSelectionChanged($scope, function(row) {
      $scope.taskSelected = gridApi.selection.getSelectedRows();
      if ($scope.taskSelected.length > 0) {
        var details = $.grep($scope.currFlow.tasks, function(elem){
          return elem.id == $scope.taskSelected[0].id;
        })[0];
        details.actions = $scope.currTask.actions;
        $scope.currTask = details;
        $scope.taskDetailsGridOptions.data = [
          {property: 'Status', value: details.status},
          {property: 'Unit name', value: details.unit.name},
          {property: 'Unit params', value: $.extend(true, [], details.unit.parameters)},
          {property: 'Input path', value: details.inputPath},
          {property: 'Output path', value: details.outputPath}
        ];
        var taskStatus = details.status.toUpperCase();
        $scope.currTask.actions = {
          act_cancel: taskStatus == 'CREATED' || taskStatus == 'NEW' || taskStatus == 'PENDING',
          act_kill: taskStatus == 'PROCESSING',
          act_resume: taskStatus == 'ON_HOLD' || taskStatus == 'FAILED' ||
          taskStatus == 'STOPPED' || taskStatus == 'CANCELED'
        };
      }
    });
  };*/

}