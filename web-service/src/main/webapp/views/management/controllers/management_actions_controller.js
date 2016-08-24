(function(){
  'use strict';

  angular.module('ubongoApp').controller('ManagementActionsController',
    ['$scope', '$http', function($scope, $http){

      var GOOD_STYLE = {paddingTop: '4px', color: 'green'};
      var BAD_STYLE = {paddingTop: '4px', color: 'red'};

      $scope.units = [];
      $scope.disabledOptionText = {value: 'Loading units...'};
      $scope.err = {
        display: false,
        msg: '',
        style: {color: 'black'}
      };

      $http.get('rest/api/units')
        .success(function(data) {
          $scope.units = data;
          if (data.length > 0) {
            $scope.disabledOptionText.value = 'Select unit';
          } else {
            $scope.disabledOptionText.value = 'No units available';
          }
        })
        .error(function() {
          $scope.disabledOptionText.value = 'Failed to load units';
        });

      $scope.generateBash = function() {
        var selectedId = $scope.selectedUnitForBash.id;
        $http.post('rest/api/units/' + selectedId, {})
          .success(function() {
            displayMsg(true, 'A request to generate bash for unit was sent to the server', GOOD_STYLE);
          })
          .error(function() {
            displayMsg(true, 'Failed to send request to generate bash for unit', BAD_STYLE);
          });
      };

      function displayMsg(disp, msg, style) {
        $scope.err.display = disp;
        $scope.err.msg = msg;
        $scope.err.style = style;
      }

    }]);

})();