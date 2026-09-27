Define = {
  China = false, --中國
  Thai = false, --泰國
  Indo = false, --印度
  Mala = false, --馬來西亞

  Free = true, --免費版
  SpecialFree = false,

  MountsEquip = true, --坐騎裝備
  Fourth = false,
  FieldBattle = false, --大陸擂台比賽
  TextCount = false, --計算檔案字數
  SuperAI = false, --內部用強化版AI
}

if Define.China then
  monCharge = 379;
elseif Define.Thai then
  monCharge = 379;
elseif Define.Indo then
  monCharge = 379;
elseif Define.Mala then
  monCharge = 379;
else
  monCharge = 350;
end

Channel = {
    Android = "Android";
    MyCard = "MyCard";
    IOS = "IOS";
}

EurrencyType = {
  TWD = "TWD";
  USD = "USD";
}

EPaymenyType = {
  GoogleIAP = "GoogleIAP";
  IOSIAP = "IOSIAP";
}

CtrlNames = {
	Prompt = "PromptCtrl",
	Message = "MessageCtrl"
}

PanelNames = {
	"PromptPanel",	
	"MessagePanel",
}

--协议类型--
ProtocalType = {
	BINARY = 0,
	PB_LUA = 1,
	PBC = 2,
	SPROTO = 3,
}

ENpcTempAttrKind = {
  None = 0,
  Upgrade = 1, --武將強化
  Reborn = 2, --脫胎換骨丸
  MoveNpc = 3, --異動
}

ECurrency = {
  Gold = 1, --元寶
  Silver = 2, --沒出
  Money = 3, --遊戲幣
  OrgGold = 4, --軍團幣
  DataFight = 5, --PVP幣
  furnitureAdvanced = 6, --高級家具扭蛋幣
  furnitureNormal = 7, --一般家具扭蛋幣
  
  Elf = 9, --精靈幣
  Treasure = 16 --祕寶抽獎券
}

EPlatform = {
  Android = 1,
  IOS = 2,
  MyCard = 3,
  AliPay = 11,
  ULong = 101,
  NineCard = 103,
}

--BINARY
DIV1 = math.pow(2, 7) + 1;
DIV2 = math.pow(2, 15) + 1;
DIV4 = math.pow(2, 31) + 1;

SUBT1 = math.pow(2, 8);
SUBT2 = math.pow(2, 16);
SUBT4 = math.pow(2, 32);

POT = {}
for i = 1, 8 do
  POT[i] = math.pow(2, 8 * (i - 1));
end

--当前使用的协议类型--
TestProtoType = ProtocalType.BINARY;

Util = LuaFramework.Util;
AppConst = LuaFramework.AppConst;
LuaHelper = LuaFramework.LuaHelper;

resMgr = LuaHelper.GetResManager();
panelMgr = LuaHelper.GetPanelManager();
soundMgr = LuaHelper.GetSoundManager();
networkMgr = LuaHelper.GetNetManager();
poolMgr = LuaHelper.GetPoolManager();


TextureFormat = {
  RGB24 = LuaHelper.TextureFormat_RGB24,
  RGBA4444 = LuaHelper.TextureFormat_RGBA4444,
}

--UnityEngine
Application = UnityEngine.Application;
NetworkReachability = UnityEngine.NetworkReachability;
SystemInfo = UnityEngine.SystemInfo;
UnityWebRequest = UnityEngine.Networking.UnityWebRequest;
GameObject = UnityEngine.GameObject;
Resources = UnityEngine.Resources;
Texture2D = UnityEngine.Texture2D;
FilterMode = UnityEngine.FilterMode;
RenderMode = UnityEngine.RenderMode;
WrapMode = UnityEngine.WrapMode;
EventTriggerType = UnityEngine.EventSystems.EventTriggerType;
Sprite = UnityEngine.Sprite;
Rect = UnityEngine.Rect;
RectTransformUtility = UnityEngine.RectTransformUtility;
Input = UnityEngine.Input;
Canvas = UnityEngine.Canvas;
Screen = UnityEngine.Screen;
AudioClip = UnityEngine.AudioClip;
AudioSource = UnityEngine.AudioSource;
KeyCode = UnityEngine.KeyCode;
TextAnchor = UnityEngine.TextAnchor;
Edge = UnityEngine.RectTransform.Edge;

--UnityEngine.UI
RawImage = UnityEngine.UI.RawImage;
Image = UnityEngine.UI.Image;
Text = UnityEngine.UI.Text;
RenderTexture = UnityEngine.RenderTexture;
GridLayoutGroup = UnityEngine.UI.GridLayoutGroup;
InputField = UnityEngine.UI.InputField;
GradientColor = UiEffect.GradientColor;
Outline = UnityEngine.UI.Outline;

--System
DateTime = System.DateTime;

--DOTween
DOTween = DG.Tweening.DOTween;
ETweenActionKind = TweenPack.ETweenActionKind;
ETweenActionCustomKind = TweenPack.ETweenActionCustomKind;
ETweenTargetKind = TweenPack.ETweenTargetKind;
ETweenAddKind = TweenPack.ETweenAddKind;

DataBuffer = LuaFramework.DataBuffer;

--Debug模式
function Define.IsDebugMode()
  return LuaHelper.CheckDefine("Debug") or LuaHelper.CheckDefine("QA") or LuaHelper.CheckDefine("ReleaseTest") or LuaHelper.CheckDefine("StayHome");
end

--內部環境
function Define.IsInternal()
  return LuaHelper.CheckDefine("Debug") or LuaHelper.CheckDefine("QA") or LuaHelper.CheckDefine("StayHome");
end
