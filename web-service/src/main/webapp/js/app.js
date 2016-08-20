angular.module('ubongoApp', ['ngRoute', 'ngMaterial', 'ngMessages', 'ui.grid', 'ui.grid.selection', 'ui.grid.moveColumns', 'ui.grid.resizeColumns', 'ui.grid.pinning', 'ui.grid.edit', 'ui.grid.draggable-rows'])
  .config(['$routeProvider', '$locationProvider', function($routeProvider, $locationProvider) {
    $routeProvider
      .when('/dashboard', {
        templateUrl: 'views/dashboard/dashboard.html',
        controller: 'DashboardController'
      })
      .when('/flows', {
        templateUrl: 'views/create-flow/create_flow.html',
        controller: 'FlowController'
      })
      .when('/management', {
        templateUrl: 'views/management/management.html',
        controller: 'ManagementController'
      })
      .otherwise({
        redirectTo: '/dashboard'
      });
    $locationProvider.html5Mode(false);
  }]);