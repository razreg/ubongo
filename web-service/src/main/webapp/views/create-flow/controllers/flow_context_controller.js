(function(){
  'use strict';

  angular.module('ubongoApp').controller('FlowContextController',
    ['$scope', '$http', function ($scope, $http) {

      var FAILED_LOADING_ANALYSIS_NAMES = 'Failed loading analysis names';

      $scope.registerContextScope($scope);
      $scope.analysis = { name: '', submitted: '5ome_rand0m_5tr1ng%$^&' };

      $scope.flow = {
        source: 'new',
        analysisNames: [],
        defaultAnalysisText: '',
        allSubjects: 'custom',
        allRuns: 'custom',
        submitMsg: '',
        submitStyle: {color: 'black'}
      };

      $scope.flowSourceAnalysis = function() {
        $scope.flow.defaultAnalysisText = 'Loading analysis names...';
        $http.get('rest/api/analyses?names=true')
          .success(function (data) {
            if (Array.isArray(data)) {
              $scope.flow.analysisNames = data;
              if (data.length > 0) {
                $scope.flow.defaultAnalysisText = 'Select analysis from list';
              } else {
                $scope.flow.defaultAnalysisText = 'No analyses available';
              }
            } else {
              $scope.flow.defaultAnalysisText = FAILED_LOADING_ANALYSIS_NAMES;
            }
          })
          .error(function () {
            $scope.flow.defaultAnalysisText = FAILED_LOADING_ANALYSIS_NAMES;
          });
      };
    }]);
})();