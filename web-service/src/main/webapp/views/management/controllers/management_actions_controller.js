(function(){
  'use strict';

  angular.module('ubongoApp').controller('ManagementActionsController',
    ['$scope', '$http', function($scope, $http){

      $scope.units = [];
      $scope.disabledOptionText = {value: 'Loading units...'};
      $http.get('rest/api/units')
        .success(function(data, status, headers, config) {
          $scope.units = data;
          if (data.length > 0) {
            $scope.disabledOptionText.value = 'Select unit';
          } else {
            $scope.disabledOptionText.value = 'No units available';
          }
        })
        .error(function(data, status, headers, config) {
          $scope.disabledOptionText.value = 'Failed to load units';
        });

      $scope.generateBash = function() {
        var selectedId = $scope.selectedUnitForBash.id;
        // TODO http request
      };

    }]);

  angular.module('ubongoApp').controller('LogsDatePicker', ['$scope', function($scope) {
    $scope.logsStartDate = new Date();
    $scope.minDate = new Date(
      $scope.logsStartDate.getFullYear(),
      $scope.logsStartDate.getMonth() - 2,
      $scope.logsStartDate.getDate());
    $scope.maxDate = new Date(
      $scope.logsStartDate.getFullYear(),
      $scope.logsStartDate.getMonth(),
      $scope.logsStartDate.getDate());
  }])
    .config(function($mdDateLocaleProvider) {
      $mdDateLocaleProvider.formatDate = function(date) {
        return moment(date).format('DD/MM/YYYY');
      };

      $mdDateLocaleProvider.parseDate = function(dateString) {
        var m = moment(dateString, 'DD/MM/YYYY', true);
        return m.isValid() ? m.toDate() : new Date(NaN);
      };
  });

})();