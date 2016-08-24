(function(){
  'use strict';

  angular.module('ubongoApp').controller('FlowController',
    ['$scope', '$http', 'ubongoNotificationsService', function ($scope, $http, ubongoNotificationsService) {

      ubongoNotificationsService.getPushNotifications();

      var GOOD_STYLE = {color: 'green'};
      var BAD_STYLE = {color: 'red'};

      $scope.runFlow = function() {

        $scope.contextScope.flowMainForm.$setPristine();
        $scope.contextScope.flow.submitMsg = '';
        $scope.contextScope.analysis.submitted = 'rand0m_me55age$@!*';

        var study = $scope.contextScope.flowMainForm.studyInput.$modelValue;
        var subject = $scope.contextScope.flow.allSubjects == 'custom' ?
          $scope.contextScope.flowMainForm.subjectInput.$modelValue : '.*';
        var _run = $scope.contextScope.flow.allRuns == 'custom' ?
          $scope.contextScope.flowMainForm.runInput.$modelValue : '.*';
        var context = {
          study: study,
          subject: subject === undefined ? '' : subject,
          run: _run === undefined ? '' : _run
        };
        var serialNumberCounter = 0;
        var flowTasks = $scope.unitsScope.unitGridOptions.data
          .map(function(unit) {
            return {
              serialNumber: serialNumberCounter++,
              unit: {
                id: unit.id,
                parameters: unit.parameters
              },
              context: context
            };
          });
        $http.post('rest/api/flows', {
            context: context,
            tasks: flowTasks
          })
          .success(function(data) {
            if (data == null || data === undefined) {
              showSubmitMsg('Something went wrong - please check if the flow exists in the Dashboard tab', BAD_STYLE);
            } else {
              executeFlow(data.flowId);
            }
          })
          .error(function() {
            showSubmitMsg('Failed to create flow', BAD_STYLE);
          });
      };

      function executeFlow(flowId) {
        $http.post('rest/api/flows/' + flowId + '?action=run', {})
          .success(function() {
            showSubmitMsg('Flow sent for execution', GOOD_STYLE);
          })
          .error(function() {
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

      $scope.loadAnalysis = function() {
        var selected = $scope.contextScope.flowMainForm.selectedAnalysis.$modelValue;
        $scope.contextScope.analysis.name = selected;
        $scope.contextScope.analysis.submitted = selected;
        $http.get('rest/api/analyses/' + selected)
          .success(function(data) {
            if (data == null || data === undefined || data.length == 0) {
              showSubmitMsg('Failed to load analysis', BAD_STYLE);
            } else {
              $scope.unitsScope.unitGridOptions.data = data.map(function(unit) {
                return {
                  id: unit.id,
                  name: unit.name,
                  description: unit.description,
                  parameters: $.extend(true, [], unit.parameters)
                };
              });
            }
          })
          .error(function() {
            showSubmitMsg('Failed to load analysis', BAD_STYLE);
          });
      };

      $scope.saveAnalysis = function() {
        $scope.contextScope.flow.submitMsg = '';
        $scope.contextScope.analysis.submitted = $scope.contextScope.analysis.name;
        var analysisNames = $scope.contextScope.flow.analysisNames;
        for (var i = 0; i < analysisNames.length; ++i) {
          if ($scope.contextScope.analysis.name == analysisNames[i]) {
            showSubmitMsg('Analysis name must be unique', BAD_STYLE);
            return;
          }
        }
        $http.post('rest/api/analyses', {
            analysisName: $scope.contextScope.analysis.name,
            units: $scope.unitsScope.unitGridOptions.data
          })
          .success(function() {
            showSubmitMsg('Analysis saved successfully', GOOD_STYLE);
          })
          .error(function() {
            showSubmitMsg('Failed to save the analysis to the DB', BAD_STYLE);
          });
      };
    }]);
})();