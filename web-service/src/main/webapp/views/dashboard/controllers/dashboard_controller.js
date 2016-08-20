(function(){
  'use strict';

  angular.module('ubongoApp').controller('DashboardController',
    ['ubongoNotificationsService', function(ubongoNotificationsService){
      ubongoNotificationsService.getPushNotifications();
  }]);
})();