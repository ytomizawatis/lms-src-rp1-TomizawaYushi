package jp.co.sss.lms.form;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * 勤怠フォーム
 * 
 * @author 冨澤雄志 - Task.25
 */
@Data
public class AttendanceForm {

	/** LMSユーザーID */
	private Integer lmsUserId;
	/** グループID */
	private Integer groupId;
	/** 年間計画No */
	private String nenkanKeikakuNo;
	/** ユーザー名 */
	private String userName;
	/** 退校フラグ */
	private Integer leaveFlg;
	/** 退校日 */
	private String leaveDate;
	/** 退校日（表示用） */
	private String dispLeaveDate;
	/** 中抜け時間(プルダウン) */
	private LinkedHashMap<Integer, String> blankTimes;
	/** 日次の勤怠フォームリスト */
	private List<DailyAttendanceForm> attendanceList;

	public void setHours(Map<Integer, String> hourMap) {
		// TODO 自動生成されたメソッド・スタブ
	}

	public void setMinutes(Map<Integer, String> minuteMap) {
		// TODO 自動生成されたメソッド・スタブ
	}
}
