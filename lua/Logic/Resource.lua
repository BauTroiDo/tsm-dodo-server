
Resource = {}
local this = Resource;

this.checkSizeDone = false;
this.assetLoaded = false;
this.dataLoaded = false;
this.imageLoaded = false;

this.rmsSize = 0;
this.dataSize = 0;
this.imageSize = 0;

this.DonwloadResource = "DonwloadResource";
this.DonwloadResourceDone = "DonwloadResourceDone";

function Resource.CheckResourceSize()
  this.rmsSize = 0;
  this.dataSize = 0;
  this.imageSize = 0;

  if LuaHelper.IsApplePlatform then
    if CGResourceManager.ReadText("_firstPatchIOS") == "" then
      CGSDK.TrackEvent("vtc_complete_first_update");
    end
  end

  CGResourceManager.CheckPackageSzie();

  while not CGResourceManager.checkPackageDone do
    coroutine.yield();
  end

  if CGResourceManager.currentDownloadSize == 0 then -- BasePackage都載完不需要更新才會檢查散檔的Resources
    CGResourceManager.CheckResourceSzie();

    while not CGResourceManager.checkPackageDone do
      coroutine.yield();
    end
  end

  this.rmsSize = CGResourceManager.currentDownloadSize;

  CGResourceManager.UpdateDataPacked(true, "Sty.dat", "Data.dat", "Ensign.dat");

  while not CGResourceManager.dataDownloadDone do
    coroutine.yield();
  end

  this.dataSize = CGResourceManager.currentDownloadSize - this.rmsSize;

  if LuaHelper.IsAndroidPlatform then
    CGResourceManager.UpdateImagePack(true, "Jma.dat", "ETCPmg.dat", "ETCJmg.dat", "CompreseData.dat");
  elseif LuaHelper.IsApplePlatform then
    CGResourceManager.UpdateImagePack(true, "Jma.dat", "PVRPmg.dat", "PVRJmg.dat", "CompreseData.dat");
  else
    CGResourceManager.UpdateImagePack(true, "Jma.dat", "DXTPmg.dat", "DXTJmg.dat", "CompreseData.dat");
  end

  while not CGResourceManager.checkImageDone do
    coroutine.yield();
  end

  this.imageSize = CGResourceManager.currentDownloadSize - this.rmsSize - this.dataSize;

  if CGResourceManager.currentDownloadSize ~= 0 then
    CGResourceManager.ShowMessage( string.format( Str_DownloadSize, CGResourceManager.currentDownloadSize * 0.000001, ( CGResourceManager.currentDepressSize + this.rmsSize ) * 0.000001 ),  this.CheckResourceSizeDone);
  else
    this.checkSizeDone = true;
  end
end

function Resource.CheckResourceSizeDone()
  this.checkSizeDone = true;

--  if not LuaHelper.IsSimpleDiskUtilsWork() then
--    this.checkSizeDone = true;
--    return;
--  end

--  if LuaHelper.GetAvailableSpace() < ( CGResourceManager.currentDepressSize + this.rmsSize ) * 0.000001 then
--    CGResourceManager.ShowMessage( Str_DiskFull, Game.Quit );
--  else
--    this.checkSizeDone = true;
--  end
end

function Resource.GetRmsSizePercent()
  return this.rmsSize / CGResourceManager.currentDownloadSize;
end

function Resource.GetDataSizePercent()
  return this.dataSize / CGResourceManager.currentDownloadSize;
end

function Resource.GetImageSizePercent()
  return this.imageSize / CGResourceManager.currentDownloadSize;
end

function Resource.LoadAllAssets()
  CGResourceManager.LoadAllAssets();

  while not CGResourceManager.loaded do
    UILoading.ShowAssetProgress();
    coroutine.yield();
  end

  this.assetLoaded = true;
end

function Resource.UpdateDataPacked()
  CGResourceManager.UpdateDataPacked(false, "Sty.dat", "Data.dat", "Ensign.dat");

  while not CGResourceManager.dataDownloadDone do
    UILoading.ShowDataProgress();
    coroutine.yield();
  end

  this.dataLoaded = true;
end

function Resource.UpdateResourcePacked()
  if LuaHelper.IsAndroidPlatform then
    CGResourceManager.UpdateImagePack(false, "CompreseData.dat", "Jma.dat", "ETCPmg.dat", "ETCJmg.dat");
  elseif LuaHelper.IsApplePlatform then
    CGResourceManager.UpdateImagePack(false, "CompreseData.dat", "Jma.dat", "PVRPmg.dat", "PVRJmg.dat");
  else
    CGResourceManager.UpdateImagePack(false, "CompreseData.dat", "Jma.dat", "DXTPmg.dat", "DXTJmg.dat");
  end

  while not CGResourceManager.imageDownloadDone do
    UILoading.ShowImageProgress();
    coroutine.yield();
  end

  this.imageLoaded = true;
end

function Resource.SaveDownloadDone()
  if this.HaveDownloadResource() then return end

  PluginManager.DownloadDone("ResourceDownloadDone");

  CGResourceManager.WriteText(this.DonwloadResourceDone, "1");
end

function Resource.SaveDownloadResource()
  if this.HaveDownloadResource() then return end

  PluginManager.StartDownload("ResourceDownloadStart");

  CGResourceManager.WriteText(this.DonwloadResource, "1");
end

function Resource.HaveDownloadResource()
  return CGResourceManager.ReadText(this.DonwloadResource) ~= "";
end

function Resource.IsNeedDownloadResource(sceneId)
  if sceneId ~= 10801 and sceneId ~= 10851 and not this.HaveDownloadResource() then
    Game.Logout(true);
    return true;
  end
 
  return false;
end

function Resource.UserCheckUpdate()
  UILoading.StartLoadAsset();
  UILoading.SetSliderValue(0);
  UI.Open(UILoading);
  CGResourceManager.ShowMessage(Str_NeedDownLoadResource, Resource.RestartGame);
end

function Resource.RestartGame()
  this.SaveDownloadResource();

  --Clear
  UI.Destroy();
  AudioManager.Destroy();
  Role.Destroy();
  MapManager.Destroy();
  EffectLight.Destroy();
  FightField.Destroy();
  FightNumber.Destroy();
  DoorManager.Destroy();
  HouseManager.Destroy();
  GdThingManager.Destroy();
  MoviePlayer.Destroy();
  JumpText.Destroy();
  
  --Restart
  Util.Restart();
end