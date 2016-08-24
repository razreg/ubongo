(function(){
  'use strict';

  angular.module('ubongoApp').controller('ManagementActionsController',
    ['$scope', '$http', function($scope, $http){

      $scope.units = [];
      $scope.disabledOptionText = {value: 'Loading units...'};
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
      };

    }]);

})();