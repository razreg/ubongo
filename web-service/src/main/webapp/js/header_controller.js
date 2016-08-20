(function(){
  'use strict';

  angular.module('ubongoApp').controller('HeaderController',
    ['$scope', 'ubongoNotificationsService', function ($scope, ubongoNotificationsService) {
      $scope.notifications = ubongoNotificationsService.notifications;
  }]);

})();