(function(){
  'use strict';

  angular.module('ubongoApp').controller('RequestsController',
    ['$scope', '$http', function ($scope, $http) {

      var NEUTRAL_STYLE = {paddingTop: '4px', color: 'black'};
      var BAD_STYLE = {paddingTop: '4px', color: 'red'};

      $scope.requests = [];
      $scope.err = {
        display: false,
        msg: '',
        style: NEUTRAL_STYLE
      };

      $scope.fetchRequests = function() {
        var limit = 19;
        $http.get('rest/api/requests?limit=' + (limit+1))
          .success(function(data, status, headers, config) {
            data = data.map(function(request) {
              var date = new Date(0);
              date.setUTCSeconds(request.lastUpdated / 1000);
              request.lastUpdated = dateInNiceFormat(date);
              date = new Date(0);
              date.setUTCSeconds(request.creationTime / 1000);
              request.creationTime = dateInNiceFormat(date);
              return request;
            });
            $scope.requests = data;
            $scope.requestGridOptions.data = $scope.requests;
            if (data.length > limit) {
              displayMsg(true, 'Showing only ' + (limit+1) + ' most recent requests', NEUTRAL_STYLE);
            }
          })
          .error(function(data, status, headers, config) {
            displayMsg(true, 'Failed to load requests', BAD_STYLE);
          });
      };
      $scope.fetchRequests();

      // requests grid
      $scope.requestGridOptions = {
        data: $scope.requests,
        enableSorting: true,
        showGridFooter: false,
        enableHorizontalScrollbar: true,
        enableVerticalScrollbar: true,
        columnDefs: [
          {name: 'id', displayName: "ID", visible: false},
          {name: 'entityId', displayName: "Entity ID"},
          {name: 'action'},
          {name: 'status'},
          {name: 'creationTime', displayName: 'Created'},
          {name: 'lastUpdated', displayName: 'Updated'}
        ]
      };

      function displayMsg(disp, msg, style) {
        $scope.err.display = disp;
        $scope.err.msg = msg;
        $scope.err.style = style;
      }

    }]);
})();