require "Controller/FurnitureController"

Furniture = {};
local this = Furniture;

this.furnitureRoot = nil;

function Furniture.Destroy()
  poolMgr:DestroyPool("FurnitureMaterial");
  poolMgr:DestroyPool("BaseFurniture");
end

function Furniture.Initialize()
  --家具根物件
  this.furnitureRoot = GameObject.Find("RoleRoot");

  --家具用材質球
  CGResourceManager.Load(
    "FurnitureMaterial",
    "",
    function(assetName, asset)
      poolMgr:CreatePool("FurnitureMaterial", asset);
    end
  );
  
  --家具物件
  CGResourceManager.Load(
    "BaseFurniture",
    "",
    function(assetName, asset)
      poolMgr:CreatePool("BaseFurniture", asset);
    end
  );

end