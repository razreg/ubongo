(function(){
  'use strict';

  angular.module('ubongoApp').controller('TasksController',
    ['$scope', '$http', 'uiGridConstants', function ($scope, $http, uiGridConstants) {

      var GOOD_STYLE = {paddingTop: '4px', color: 'green'};
      var BAD_STYLE = {paddingTop: '4px', color: 'red'};

      $scope.defaultFlowText = {value: 'Loading flows...'};
      $scope.flows = [];
      $scope.err = {
        display: false,
        msg: '',
        style: {color: 'black'}
      };
      $scope.selectedOption = '0';
      $scope.currFlow = {status: 'NEW'};

      $scope.currTask = {
        actions: {
          act_cancel: false,
          act_kill: false,
          act_resume: false
        }
      };
      reloadFlows();

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
        gridApi.selection.on.rowSelectionChanged($scope, function() {
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
              {property: 'Subject', value: details.context.subject},
              {property: 'Run', value: details.context.run},
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

      function reloadFlows() {
        return $http.get('rest/api/flows')
          .success(function(data) {
            $scope.flows = data;
            if ($scope.flows.length > 0) {
              $scope.defaultFlowText.value = 'Select flow';
            } else {
              $scope.defaultFlowText.value = 'No flows available';
            }
            return true;
          })
          .error(function() {
            $scope.defaultFlowText.value = 'Failed to load flows';
            return false;
          });
      }

      $scope.refreshFlows = function() {
        $scope.taskGridOptions.data = [];
        $scope.taskDetailsGridOptions.data = [];
        $scope.taskSelected = [];
        var success = reloadFlows();
        if (success) {
          $scope.loadFlow();
        }
      };

      $scope.loadFlow = function() {
        displayMsg(false);
        $scope.taskSelected = [];
        $http.get('rest/api/flows/' + $scope.selectedOption + '/tasks')
          .success(function(data) {
            if (data.length > 0) {
              var flowData = $scope.flows.filter(function(flow) {
                return flow.flowId+'' == $scope.selectedOption;
              })[0];
              $scope.currFlow = {
                tasks: data,
                context: flowData.context,
                status: flowData.status
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
          .error(function() {
            $scope.currFlow = {status: 'New'};
            displayMsg(true, 'Failed to load flow details', BAD_STYLE);
            $scope.taskGridOptions.data = [];
          });
      };

      $scope.cancelFlow = function() {
        $http.post('rest/api/flows/' + $scope.selectedOption + '?action=cancel', {})
          .success(function() {
            displayMsg(true, 'A request to cancel flow was sent to the server', GOOD_STYLE);
          })
          .error(function() {
            displayMsg(true, 'Failed to cancel flow', BAD_STYLE);
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
        $http.post(getApiTaskActionPath('cancel'), {})
          .success(function() {
            displayMsg(true, 'A request to cancel task was sent to the server', GOOD_STYLE);
          })
          .error(function() {
            displayMsg(true, 'Failed to cancel task', BAD_STYLE);
          });
      };

      $scope.resumeTask = function() {
        $http.post(getApiTaskActionPath('resume'), {})
          .success(function() {
            displayMsg(true, 'A request to resume task was sent to the server', GOOD_STYLE);
          })
          .error(function() {
            displayMsg(true, 'Failed to resume task', BAD_STYLE);
          });
      };

      $scope.killTask = function() {
        $http.post(getApiTaskActionPath('stop'), {})
          .success(function() {
            displayMsg(true, 'A request to stop task was sent to the server', GOOD_STYLE);
          })
          .error(function() {
            displayMsg(true, 'Failed to stop task', BAD_STYLE);
          });
      };

      function getApiTaskActionPath(action) {
        return 'rest/api/flows/' + $scope.selectedOption + '/tasks/' + $scope.currTask.id + '?action=' + action;
      }
    }])

})();