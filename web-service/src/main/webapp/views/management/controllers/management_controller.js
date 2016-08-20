(function(){
  'use strict';

  angular.module('ubongoApp').controller('ManagementController',
    ['ubongoNotificationsService', function(ubongoNotificationsService){
      ubongoNotificationsService.stopPushNotifications();
    }]);
})();