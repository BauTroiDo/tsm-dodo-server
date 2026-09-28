Notification = {}
local this = Notification;

local androidFileName = "Notification.txt";

local maxDay = 3;
local expTime = {12, 18, 22}

local startTime;

function Notification.Init()
  LuaHelper.InitNotification();
  this.SetNotification();
end

function Notification.SetNotification()
  if CGTimer.serverTime == nil then return end

  LuaHelper.ClearAllNotifications(androidFileName);

--  --1.離線掛機8小時滿 --暫時不加入
--  if isDissconnect then
--    LuaHelper.AddNotifications(28800, string.Get(21532), "", string.Get(21533));
--  end

  --2.固定福神領取時間(中午12:00 晚上18:00 跟 22:00) 存三天份
  startTime = System.DateTime.New(CGTimer.serverTime.Year, CGTimer.serverTime.Month, CGTimer.serverTime.Day, 0, 0, 0, 0);
  for i = 1, maxDay do
    for m = 1, #expTime do  
      startTime = System.DateTime.New(startTime.Year, startTime.Month, startTime.Day, expTime[m], 0, 0, 0);   
     if (System.DateTime.Compare(startTime, CGTimer.serverTime) > 0) then
        LuaHelper.AddNotifications(startTime:Subtract(CGTimer.serverTime).TotalSeconds, string.Get(21532), string.format(string.Get(21534), expTime[m]), string.format(string.Get(21534), expTime[m]));
      end
    end
    startTime = startTime:AddDays(1);  
  end

  LuaHelper.SaveNotifications(androidFileName);
end