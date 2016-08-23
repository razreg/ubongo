(function(){
  'use strict';

  angular.module('ubongoApp').controller('MachinesController',
    ['$scope', '$http', '$interval', function ($scope, $http, $interval) {

      var GOOD_STYLE = {paddingTop: '4px', color: 'green'};
      var BAD_STYLE = {paddingTop: '4px', color: 'red'};
      var SERVER_DOWN = {color: 'red', fontWeight: 'bold'};

      fetchMachines();
      $interval(fetchMachines, 1000 * 30); // refresh machines every 30 seconds
      $scope.machines = [];
      $scope.err = {
        display: false,
        msg: '',
        style: {color: 'black'}
      };
      $scope.currMachine = {id: -1};
      $scope.server = {
        lastHeartbeat: '-',
        style: {}
      };

      function fetchMachines() {
        $http.get('rest/api/machines')
          .success(function(data) {
            var machinesWithoutServer = [];
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
              if (data[i].id*1 != 0) {
                machinesWithoutServer.push(data[i]);
                $scope.currMachine = {id: -1};
              } else {
                $scope.server.lastHeartbeat = data[i].lastHeartbeat;
                if (data[i].connected) {
                  $scope.server.style = {};
                } else {
                  $scope.server.style = SERVER_DOWN;
                }
              }
            }
            $scope.machines = machinesWithoutServer;
            $scope.machineGridOptions.data = $scope.machines;
          })
          .error(function() {
            displayMsg(true, 'Failed to load machines', BAD_STYLE);
          });
      }

      function isDateTooOld(utcSeconds) {
        return new Date().getTime()/1000 - utcSeconds > 60 * 3; // 3 minutes interval to detect disconnected machine
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
        gridApi.selection.on.rowSelectionChanged($scope, function() {
          $scope.err.display = false;
          $scope.machinesSelected = gridApi.selection.getSelectedRows();
          if ($scope.machinesSelected.length > 0) {
            $scope.currMachine = $scope.machinesSelected[0];
          } else {
            $scope.currMachine = {id: -1};
          }
        });
      };

      $scope.changeActivationStatus = function() {
        var action = ($scope.currMachine.active ? 'de' : '') + 'activate';
        $http.post('rest/api/machines/' + $scope.currMachine.id + '?' + action + '=true')
          .success(function() {
            displayMsg(true, 'A request to ' + action + ' machine was sent to the server', GOOD_STYLE);
          })
          .error(function() {
            displayMsg(true, 'Failed to ' + action + ' machine', BAD_STYLE);
          });
      };

      function displayMsg(disp, msg, style) {
        $scope.err.display = disp;
        $scope.err.msg = msg;
        $scope.err.style = style;
      }

    }])
})();