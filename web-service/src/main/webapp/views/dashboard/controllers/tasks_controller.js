function TasksController($scope, $http, uiGridConstants) {

  var GOOD_STYLE = {paddingTop: '4px', color: 'green'};
  var BAD_STYLE = {paddingTop: '4px', color: 'red'};

  $scope.defaultFlowText = {value: 'Loading flows...'};
  $scope.flows = [];
  $scope.err = {
    display: false,
    msg: '',
    style: {color: 'black'}
  };
  $scope.currFlow = {status: 'New'};
  $scope.currTask = {
    actions: {
      act_cancel: false,
      act_kill: false,
      act_resume: false
    }
  };
  $http.get('rest/api/flows')
    .success(function(data, status, headers, config) {
      $scope.flows = data;
      if ($scope.flows.length > 0) {
        $scope.defaultFlowText.value = 'Select flow';
      } else {
        $scope.defaultFlowText.value = 'No flows available';
      }
    })
    .error(function(data, status, headers, config) {
      $scope.defaultFlowText.value = 'Failed to load flows';
    });

  // tasks grid
  $scope.taskGridOptions = {
    data: [],
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
      {name: 'id', displayName: "ID"},
      {name: 'status'},
      {name: 'serialNumber', type: 'number', sort: {
        direction: uiGridConstants.ASC,
        priority: 0
      }, visible: false},
      {name: 'unit'},
      {name: 'machine'}
    ]
  };
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
  };

  function displayMsg(disp, msg, style) {
    $scope.err.display = disp;
    $scope.err.msg = msg;
    $scope.err.style = style;
  }

  $scope.loadFlow = function() {
    displayMsg(false);
    $http.get('rest/api/flows/' + $scope.selectFlow.flowId + '/tasks')
      .success(function(data, status, headers, config) {
        if (data.length > 0) {
          $scope.currFlow = {
            tasks: data,
            context: data[0].context,
            status: $scope.selectFlow.status
          };
          $scope.taskGridOptions.data = data.map(function(task) {
            return {
              id: task.id,
              serialNumber: task.serialNumber,
              status: task.status,
              unit: task.unit.name,
              machine: task.machine == null || task.machine === undefined || typeof task.machine === 'string' ?
                '' : task.machine.id
            };
          });
        } else {
          $scope.taskGridOptions.data = [];
        }
      })
      .error(function(data, status, headers, config) {
        $scope.currFlow = {status: 'New'};
        displayMsg(true, 'Failed to load flow details', BAD_STYLE);
        $scope.taskGridOptions.data = [];
      });
  };

  // taskDetails grid
  $scope.taskDetailsGridOptions = {
    data: [],
    enableSorting: false,
    enableRowSelection: false,
    showGridFooter: false,
    enableRowHeaderSelection: false,
    enableSelectionBatchEvent: false,
    enableHorizontalScrollbar: true,
    columnDefs: [
      {name: 'property'},
      {name: 'value'}
    ]
  };

  $scope.cancelTask = function() {
    $http.post(getApiTaskActionPath('cancel'))
      .success(function(data, status, headers, config) {
        displayMsg(true, 'Task canceled successfully', GOOD_STYLE);
      })
      .error(function(data, status, headers, config) {
        displayMsg(true, 'Failed to cancel task', BAD_STYLE);
      });
  };

  $scope.resumeTask = function() {
    $http.post(getApiTaskActionPath('resume'))
      .success(function(data, status, headers, config) {
        displayMsg(true, 'Task resumed successfully', GOOD_STYLE);
      })
      .error(function(data, status, headers, config) {
        displayMsg(true, 'Failed to resume task', BAD_STYLE);
      });
  };

  $scope.killTask = function() {
    $http.post(getApiTaskActionPath('stop'))
      .success(function(data, status, headers, config) {
        displayMsg(true, 'A request to stop task was sent to the server', GOOD_STYLE);
      })
      .error(function(data, status, headers, config) {
        displayMsg(true, 'Failed to stop task', BAD_STYLE);
      });
  };

  function getApiTaskActionPath(action) {
    return 'rest/api/flows/' + $scope.selectFlow.flowId + '/tasks/' + $scope.currTask.id + '?action=' + action;
  }
}