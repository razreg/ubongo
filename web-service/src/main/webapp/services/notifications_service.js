(function(){
  'use strict';

  angular.module('ubongoApp')
    .service('ubongoNotificationsService', function($http, $interval) {

      var intervalPromise = null;
      var notifications = {
        requests: 0,
        lastRequest: new Date().getTime()
      };
      this.notifications = notifications;

      function countRequests() {
        var date = new Date();
        var currTime = date.getTime() - date.getUTCMilliseconds();
        $http.get('/rest/api/requests?count=true&t=' + notifications.lastRequest)
          .success(function(data, status, headers, config) {
            notifications.lastRequest = currTime;
            notifications.requests = notifications.requests + data*1;
          })
          .error(function(data, status, headers, config) {
            notifications.requests = 0;
          });
      }

      this.getPushNotifications = function() {
        if (intervalPromise == null) {
          clearNotifications();
          intervalPromise = $interval(countRequests, 1000 * 10);
        }
      };

      this.stopPushNotifications = function() {
        if (intervalPromise != null) {
          $interval.cancel(intervalPromise);
          intervalPromise = null;
        }
        clearNotifications();
      };

      function clearNotifications() {
        notifications.lastRequest = new Date().getTime();
        notifications.requests = 0;
      }

  });

})();