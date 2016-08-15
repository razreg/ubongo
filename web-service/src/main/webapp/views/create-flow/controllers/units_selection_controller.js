function UnitsSelectionController($scope, $http) {

  $scope.registerUnitsScope($scope);
  $scope.units = [];
  $scope.defaultUnitText = {value: 'Loading units...'};
  $http.get('/rest/api/units')
    .success(function(data, status, headers, config) {
      $scope.units = data;
      if (data.length > 0) {
        $scope.defaultUnitText.value = 'Select unit';
      } else {
        $scope.defaultUnitText.value = 'No units available';
      }
    })
    .error(function(data, status, headers, config) {
      $scope.defaultUnitText.value = 'Failed to load units';
    });

  // units grid
  $scope.unitGridOptions = {
    data: [],
    enableSorting: false,
    enableSelectAll: false,
    multiSelect: false,
    enableRowSelection: false,
    noUnselect: false,
    showGridFooter: false,
    enableRowHeaderSelection: true,
    enableSelectionBatchEvent: false,
    enableFullRowSelection: false,
    enableHorizontalScrollbar: true,
    columnDefs: [
      {name: 'id', width: '40', enableColumnMenu: false, enableHiding: false},
      {name: 'name'},
      {name: 'description'},
      {name: 'parameters'}
    ],
    rowTemplate:
      '<div grid="grid" class="ui-grid-draggable-row" draggable="true">' +
        '<div ng-repeat="(colRenderIndex, col) in colContainer.renderedColumns track by col.colDef.name" ' +
        'class="ui-grid-cell" ng-class="{ \'ui-grid-row-header-cell\': col.isRowHeader, \'custom\': true }" ' +
        'ui-grid-cell></div>' +
      '</div>'
  };
  $scope.unitGridOptions.onRegisterApi = function(gridApi) {
    //set gridApi on scope
    $scope.gridApi = gridApi;
    gridApi.selection.on.rowSelectionChanged($scope, function(row) {
      $scope.unitSelected = gridApi.selection.getSelectedRows();
      if ($scope.unitSelected.length > 0) {
        $scope.paramGridOptions.data = $scope.unitSelected[0].parameters;
      }
    });
  };

  $scope.addUnit = function() {
    var optSelected = $scope.addUnitSelect;
    $scope.unitGridOptions.data.push({
      id: optSelected.id,
      name: optSelected.name,
      description: optSelected.description,
      parameters: $.extend(true, [], optSelected.parameters)
    });
  };

  $scope.removeUnit = function() {
    angular.forEach($scope.gridApi.selection.getSelectedRows(), function (data, index) {
      $scope.gridApi.selection.unSelectRow(data);
      $scope.unitGridOptions.data.splice($scope.unitGridOptions.data.lastIndexOf(data), 1);
    });
  };

  // params grid
  $scope.paramGridOptions = {
    data: [],
    enableSorting: false,
    showGridFooter: false,
    enableHorizontalScrollbar: true,
    enablePinning: false,
    columnDefs: [
      {name: 'display', displayName: 'Name', width: '25%', pinnedLeft: true, enableHiding: false, enableCellEdit: false},
      {name: 'value', enableHiding: false, enableCellEdit: true}
    ]
  };

}