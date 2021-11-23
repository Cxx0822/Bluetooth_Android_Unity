using System.Collections.Generic;
using UnityEngine;
using System.IO.Ports;
using System.Threading;
using UnityEngine.UI;
using System.Text;

public class TestBluetoothReceive : MonoBehaviour
{
	// 串口设置
	private string portName = "COM5";
	private int baudRate = 9600;
	private Parity parity = Parity.Odd;
	private int dataBits = 8;
	private StopBits stopBits = StopBits.One;

	// 串口对象
	private SerialPort sp = null;
	// 读取数据线程对象
	private Thread dataReceiveThread = null;
	// 是否接收数据
	private bool canRecieveMsg = true;
	// 接收到的数据
	string strReceived;
	private bool IsOpenSerial = false;

	private InputField bluetoothText;


	void Awake()
	{
		strReceived = string.Empty;
	}

	// Use this for initialization
	void Start()
	{
		List<string> btnsName = new List<string>();
		btnsName.Add("BtnOpenSerial");
		btnsName.Add("BtnCloseSerial");
		btnsName.Add("BtnSend");

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

		// 实例化对象
		sp = new SerialPort(portName, baudRate, parity, dataBits, stopBits);
		// OpenPort();
	}

	public void OnClick(GameObject sender)
	{
		switch (sender.name)
		{
			case "BtnOpenSerial":
				OpenPort();
				break;
			case "BtnCloseSerial":
				ClosePort();
				break;
			case "BtnSend":
				BtnSend();
				break;
			default:
				break;
		}
	}

	public void OpenPort()
	{
		// 读取时间
		sp.ReadTimeout = 100;
		try
		{
			sp.Open();
			Debug.Log("open success");

			// 实例化读取数据线程
			this.dataReceiveThread = new Thread(new ThreadStart(DataReceiveFunction));
			this.dataReceiveThread.IsBackground = true;
			this.dataReceiveThread.Start();
			IsOpenSerial = true;

		}
		catch (System.Exception ex)
		{
			Debug.Log(ex.Message);
		}

		
	}

	public void ClosePort()
	{
		try
		{
			sp.Close();
			Debug.Log("close success");
			dataReceiveThread.Abort();
			
		}
		catch (System.Exception ex)
		{
			Debug.Log(ex.Message);
		}
	}

	    /// <summary>
    /// 可以读取多个字符，即字节数组；但是需要的平台的是.net 4.6
    /// 将 Scripting Runing Vision 和Api Compatibility都改为.Net 4.6
    /// </summary>
	void DataReceiveFunction()
	{
		try
		{
			while (canRecieveMsg)
			{
				// 设定读取间隔
				Thread.Sleep(25);
				if (!sp.IsOpen)
					return;
				int datalength = sp.BytesToRead;
				if (datalength == 0)
				{
					continue;
				}

				byte[] bytes = new byte[datalength];
				sp.Read(bytes, 0, datalength);
				strReceived = System.Text.Encoding.Default.GetString(bytes);

			}
		}
		catch (System.Exception ex)
		{
			if (ex.GetType() != typeof(ThreadAbortException))
			{
			}
			Debug.Log(ex);
		}
	}

	public void handleReceivedData(string str)
    {
		if (str != "")
		{
			bluetoothText.text = str;
		}
	}


    private void Update()
    {
		if (IsOpenSerial)
		{
			handleReceivedData(strReceived);
			strReceived = string.Empty;
		}
	}

	void OnApplicationQuit()
	{
		canRecieveMsg = false;
		ClosePort();
	}

	public void BtnSend()
	{
		byte[] bytes = Encoding.GetEncoding("utf-8").GetBytes(bluetoothText.text);
		sp.Write(bytes, 0, bytes.Length);
	}
}