function FlowController($scope, $http) {

  var GOOD_STYLE = {color: 'green'};
  var BAD_STYLE = {color: 'red'};

  $scope.runFlow = function() {

    $scope.contextScope.flowMainForm.$setPristine();
    $scope.contextScope.flow.submitMsg = '';

    var study = $scope.contextScope.flowMainForm.studyInput.$modelValue;
    var subject = $scope.contextScope.flow.allSubjects == 'custom' ?
      $scope.contextScope.flowMainForm.subjectInput.$modelValue : '.*';
    var _run = $scope.contextScope.flow.allRuns == 'custom' ?
      $scope.contextScope.flowMainForm.runInput.$modelValue : '.*';
    var serialNumberCounter = 0;
    var flowTasks = $scope.unitsScope.unitGridOptions.data
      .map(function(unit) {
        return {
          serialNumber: serialNumberCounter++,
          unit: {
            id: unit.id,
            parameters: unit.parameters
          },
          context: {
            study: study,
            subject: subject === undefined ? '' : subject,
            run: _run === undefined ? '' : _run
          }
        };
      });
    $http.post('/rest/api/flows', {
        studyName: study,
        tasks: flowTasks
      })
      .success(function(data, status, headers, config) {
        if (data == null || data === undefined) {
          // TODO reflect error in UI
        } else {
          executeFlow(data.flowId);
        }
      })
      .error(function(data, status, headers, config) {
        showSubmitMsg('Failed to create flow', BAD_STYLE);
      });
  };

  function executeFlow(flowId) {
    $http.post('/rest/api/flows/' + flowId + '?action=run')
      .success(function(data, status, headers, config) {
        showSubmitMsg('Flow sent for execution', GOOD_STYLE);
        // TODO make sure there will be no submission of a duplicate flow (disable the button)
      })
      .error(function(data, status, headers, config) {
        showSubmitMsg('Flow created but not executed', BAD_STYLE);
      });
  }

  $scope.registerUnitsScope = function(childScope) {
    $scope.unitsScope = childScope;
  };

  $scope.registerContextScope = function(childScope) {
    $scope.contextScope = childScope;
  };

  function showSubmitMsg(msg, style) {
    $scope.contextScope.flow.submitMsg = msg;
    $scope.contextScope.flow.submitStyle = style;
  }

}