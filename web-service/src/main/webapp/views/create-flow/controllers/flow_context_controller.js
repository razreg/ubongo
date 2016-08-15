function FlowContextController($scope, $http) {

  var FAILED_LOADING_ANALYSIS_NAMES = 'Failed loading analysis names';

  $scope.registerContextScope($scope);

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
    if ($scope.flow.analysisNames.length === 0) {
      $scope.flow.defaultAnalysisText = 'Loading analysis names...';
      $http.get('/rest/api/analyses/names')
        .success(function (data, status, headers, config) {
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
        .error(function (data, status, headers, config) {
          $scope.flow.defaultAnalysisText = FAILED_LOADING_ANALYSIS_NAMES;
        });
    }
  };
}