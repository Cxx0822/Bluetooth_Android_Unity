using System;
using System.Collections;
using System.Collections.Generic;
using System.Text;
using UnityEngine;
using UnityEngine.UI;

public class TestBluetoothSend : MonoBehaviour
{
    private AndroidJavaClass jc;
    private AndroidJavaObject jo;
    private string deviceNameStr;

    private Dropdown dpn;

    private InputField bluetoothText;

    // Start is called before the first frame update
    void Start()
    {
        //获得com.unity3d.player.UnityPlayer 下的类，对于扩展的Activity 是一个固定的写法。只要记住就行了
        jc = new AndroidJavaClass("com.unity3d.player.UnityPlayer");
        //获得 jc 类中的 currentActivity 对象，也是一种固定的写法
        jo = jc.GetStatic<AndroidJavaObject>("currentActivity");

        // 添加按钮对象
        List<string> btnsName = new List<string>();
        btnsName.Add("BtnOpen");
        btnsName.Add("BtnDisconnect");

        btnsName.Add("BtnSend");
        btnsName.Add("BtnReceive");

        // 输入控件
        bluetoothText = GameObject.Find("Canvas/BluetoothText").GetComponent<InputField>();

        foreach (string btnName in btnsName)
        {
            GameObject btnObj = GameObject.Find(btnName);
            Button btn = btnObj.GetComponent<Button>();
            btn.onClick.AddListener(delegate () {
                this.OnClick(btnObj);
            });
        }

        // 添加下拉菜单选项
        GameObject dpnObj = GameObject.Find("DpnDeviceSelect");
        dpn = dpnObj.GetComponent<Dropdown>();
        dpn.ClearOptions();
        dpn.onValueChanged.AddListener(DpnDeviceSelect);//监听点击

    }

    // 响应按钮点击事件
    public void OnClick(GameObject sender)
    {
        switch (sender.name)
        {
            case "BtnOpen":
                BtnOpen();
                break;
            case "BtnDisconnect":
                jo.Call("onDisconnect");
                break;
            case "BtnSend":
                BtnSend();
                break;
            case "BtnReceive":
                BtnReceice();
                break;
            default:
                break;
        }
    }

    public void BtnOpen()
    {
        jo.Call("onOpen");
        deviceNameStr = jo.Call<string>("onScan");

        dpn.ClearOptions();
        dpn.options.Clear();

        string[] deviceNameList = deviceNameStr.Split(',');
        for (int i = 0; i < deviceNameList.Length - 1; i++)
        {
            Dropdown.OptionData data = new Dropdown.OptionData();
            data.text = deviceNameList[i].ToString();
            dpn.options.Add(data);
        }
    }

    public void DpnDeviceSelect(int n)
    {
        jo.Call("onConnect", dpn.captionText.text);
    }

    public void BtnSend()
    {
        string value = bluetoothText.text;
        jo.Call("onSendMessage", value);
    }

    public void BtnReceice()
    {
        string readMessage = jo.Call<string>("onReadMessage");

        // 和发送端保持一致
        UTF8Encoding utf8 = new UTF8Encoding();
        Byte[] encodedBytes = utf8.GetBytes(readMessage);
        String decodedString = utf8.GetString(encodedBytes);

        bluetoothText.text = decodedString;
    }
}
