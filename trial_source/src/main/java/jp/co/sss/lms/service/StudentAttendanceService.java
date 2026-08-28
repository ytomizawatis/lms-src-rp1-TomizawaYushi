package jp.co.sss.lms.service;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindingResult;

import jp.co.sss.lms.dto.AttendanceManagementDto;
import jp.co.sss.lms.dto.LoginUserDto;
import jp.co.sss.lms.entity.TStudentAttendance;
import jp.co.sss.lms.enums.AttendanceStatusEnum;
import jp.co.sss.lms.form.AttendanceForm;
import jp.co.sss.lms.form.DailyAttendanceForm;
import jp.co.sss.lms.mapper.TStudentAttendanceMapper;
import jp.co.sss.lms.util.AttendanceUtil;
import jp.co.sss.lms.util.Constants;
import jp.co.sss.lms.util.DateUtil;
import jp.co.sss.lms.util.LoginUserUtil;
import jp.co.sss.lms.util.MessageUtil;
import jp.co.sss.lms.util.TrainingTime;

/**
 * 勤怠情報（受講生入力）サービス
 * 
 * @author 冨澤雄志 - Task.25
 */
@Service
public class StudentAttendanceService {

	@Autowired
	private TrainingTime trainingTime;
	@Autowired
	private DateUtil dateUtil;
	@Autowired
	private AttendanceUtil attendanceUtil;
	@Autowired
	private MessageUtil messageUtil;
	@Autowired
	private LoginUserUtil loginUserUtil;
	@Autowired
	private LoginUserDto loginUserDto;
	/*@Autowired
	private MLmsUserMapper mLmsUserMapper;
	@Autowired
	private MPlaceMapper mPlaceMapper;
	@Autowired
	private TCompanyAttendanceMapper tCompanyAttendanceMapper;
	@Autowired
	private TUserPlaceMapper tUserPlaceMapper;*/
	@Autowired
	private TStudentAttendanceMapper tStudentAttendanceMapper;
	/* @Autowired
	private PlaceService placeService;
	@Autowired
	private CourseService courseService;
	@Autowired
	private CompanyService companyService;*/

	/**
	 * 勤怠一覧情報取得
	 * 
	 * @param courseId
	 * @param lmsUserId
	 * @return 勤怠管理画面用DTOリスト
	 */
	public List<AttendanceManagementDto> getAttendanceManagement(Integer courseId,
			Integer lmsUserId) {

		// 勤怠管理リストの取得
		List<AttendanceManagementDto> attendanceManagementDtoList = tStudentAttendanceMapper
				.getAttendanceManagement(courseId, lmsUserId, Constants.DB_FLG_FALSE);
		for (AttendanceManagementDto dto : attendanceManagementDtoList) {
			// 中抜け時間を設定
			if (dto.getBlankTime() != null) {
				TrainingTime blankTime = attendanceUtil.calcBlankTime(dto.getBlankTime());
				dto.setBlankTimeValue(String.valueOf(blankTime));
			}
			// 遅刻早退区分判定
			AttendanceStatusEnum statusEnum = AttendanceStatusEnum.getEnum(dto.getStatus());
			if (statusEnum != null) {
				dto.setStatusDispName(statusEnum.name);
			}
		}

		return attendanceManagementDtoList;
	}

	/**
	 * 出退勤更新前のチェック
	 * 
	 * @param attendanceType
	 * @return エラーメッセージ
	 */
	public String punchCheck(Short attendanceType) {
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 権限チェック
		if (!loginUserUtil.isStudent()) {
			return messageUtil.getMessage(Constants.VALID_KEY_AUTHORIZATION);
		}
		// 研修日チェック
		if (!attendanceUtil.isWorkDay(loginUserDto.getCourseId(), trainingDate)) {
			return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_NOTWORKDAY);
		}
		// 登録情報チェック
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		switch (attendanceType) {
		case Constants.CODE_VAL_ATWORK:
			if (tStudentAttendance != null
					&& !tStudentAttendance.getTrainingStartTime().equals("")) {
				// 本日の勤怠情報は既に入力されています。直接編集してください。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHALREADYEXISTS);
			}
			break;
		case Constants.CODE_VAL_LEAVING:
			if (tStudentAttendance == null
					|| tStudentAttendance.getTrainingStartTime().equals("")) {
				// 出勤情報がないため退勤情報を入力出来ません。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHINEMPTY);
			}
			if (!tStudentAttendance.getTrainingEndTime().equals("")) {
				// 本日の勤怠情報は既に入力されています。直接編集してください。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHALREADYEXISTS);
			}
			TrainingTime trainingStartTime = new TrainingTime(
					tStudentAttendance.getTrainingStartTime());
			TrainingTime trainingEndTime = new TrainingTime();
			if (trainingStartTime.compareTo(trainingEndTime) > 0) {
				// 退勤時刻は出勤時刻より後でなければいけません。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_TRAININGTIMERANGE);
			}
			break;
		}
		return null;
	}

	/**
	 * 出勤ボタン処理
	 * 
	 * @return 完了メッセージ
	 */
	public String setPunchIn() {
		// 当日日付
		Date date = new Date();
		// 本日の研修日
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 現在の研修時刻
		TrainingTime trainingStartTime = new TrainingTime();
		// 遅刻早退ステータス
		AttendanceStatusEnum attendanceStatusEnum = attendanceUtil.getStatus(trainingStartTime,
				null);
		// 研修日の勤怠情報取得
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		if (tStudentAttendance == null) {
			// 登録処理
			tStudentAttendance = new TStudentAttendance();
			tStudentAttendance.setLmsUserId(loginUserDto.getLmsUserId());
			tStudentAttendance.setTrainingDate(trainingDate);
			tStudentAttendance.setTrainingStartTime(trainingStartTime.toString());
			tStudentAttendance.setTrainingEndTime("");
			tStudentAttendance.setStatus(attendanceStatusEnum.code);
			tStudentAttendance.setNote("");
			tStudentAttendance.setAccountId(loginUserDto.getAccountId());
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			tStudentAttendance.setFirstCreateUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setFirstCreateDate(date);
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			tStudentAttendance.setBlankTime(null);
			tStudentAttendanceMapper.insert(tStudentAttendance);
		} else {
			// 更新処理
			tStudentAttendance.setTrainingStartTime(trainingStartTime.toString());
			tStudentAttendance.setStatus(attendanceStatusEnum.code);
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			tStudentAttendanceMapper.update(tStudentAttendance);
		}
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	/**
	 * 退勤ボタン処理
	 * 
	 * @return 完了メッセージ
	 */
	public String setPunchOut() {
		// 当日日付
		Date date = new Date();
		// 本日の研修日
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 研修日の勤怠情報取得
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		// 出退勤時刻
		TrainingTime trainingStartTime = new TrainingTime(
				tStudentAttendance.getTrainingStartTime());
		TrainingTime trainingEndTime = new TrainingTime();
		// 遅刻早退ステータス
		AttendanceStatusEnum attendanceStatusEnum = attendanceUtil.getStatus(trainingStartTime,
				trainingEndTime);
		// 更新処理
		tStudentAttendance.setTrainingEndTime(trainingEndTime.toString());
		tStudentAttendance.setStatus(attendanceStatusEnum.code);
		tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
		tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
		tStudentAttendance.setLastModifiedDate(date);
		tStudentAttendanceMapper.update(tStudentAttendance);
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	/**
	 * 勤怠フォームへ設定
	 * 
	 * @author 冨澤雄志 - Task.26
	 * @param attendanceManagementDtoList
	 * @return 勤怠編集フォーム
	 */
	public AttendanceForm setAttendanceForm(
			List<AttendanceManagementDto> attendanceManagementDtoList) {

		AttendanceForm attendanceForm = new AttendanceForm();
		attendanceForm.setAttendanceList(new ArrayList<DailyAttendanceForm>());
		attendanceForm.setLmsUserId(loginUserDto.getLmsUserId());
		attendanceForm.setUserName(loginUserDto.getUserName());
		attendanceForm.setLeaveFlg(loginUserDto.getLeaveFlg());
		attendanceForm.setBlankTimes(attendanceUtil.setBlankTime());

		Map<Integer, String> hourMap = new LinkedHashMap<>();
		hourMap.put(null, "");

		for (int i = 0; i < 24; i++) {
			hourMap.put(i, String.format("%02d", i));
		}

		Map<Integer, String> minuteMap = new LinkedHashMap<>();
		minuteMap.put(null, "");

		for (int i = 0; i < 60; i++) {
			minuteMap.put(i, String.format("%02d", i));
		}

		attendanceForm.setHours(hourMap);
		attendanceForm.setMinutes(minuteMap);

		// 途中退校している場合のみ設定
		if (loginUserDto.getLeaveDate() != null) {
			attendanceForm
					.setLeaveDate(dateUtil.dateToString(loginUserDto.getLeaveDate(), "yyyy-MM-dd"));
			attendanceForm.setDispLeaveDate(
					dateUtil.dateToString(loginUserDto.getLeaveDate(), "yyyy年M月d日"));
		}

		// 勤怠管理リストの件数分、日次の勤怠フォームに移し替え
		for (AttendanceManagementDto attendanceManagementDto : attendanceManagementDtoList) {
			DailyAttendanceForm dailyAttendanceForm = new DailyAttendanceForm();
			dailyAttendanceForm
					.setStudentAttendanceId(attendanceManagementDto.getStudentAttendanceId());
			dailyAttendanceForm
					.setTrainingDate(dateUtil.toString(attendanceManagementDto.getTrainingDate()));
			dailyAttendanceForm
					.setTrainingStartTime(attendanceManagementDto.getTrainingStartTime());
			dailyAttendanceForm.setTrainingEndTime(attendanceManagementDto.getTrainingEndTime());

			// Task.26: DBの出勤時刻「hh:mm」を「時」と「分」に分けてフォームへ設定する
			if (attendanceManagementDto.getTrainingStartTime() != null
					&& !attendanceManagementDto.getTrainingStartTime().isEmpty()) {
				String[] trainingStartTime = attendanceManagementDto.getTrainingStartTime().split(":");
				dailyAttendanceForm.setTrainingStartTimeHour(trainingStartTime[0]);
				dailyAttendanceForm.setTrainingStartTimeMinute(trainingStartTime[1]);
			}

			// Task.26: DBの退勤時刻「hh:mm」を「時」と「分」に分けてフォームへ設定する
			if (attendanceManagementDto.getTrainingEndTime() != null
					&& !attendanceManagementDto.getTrainingEndTime().isEmpty()) {
				String[] trainingEndTime = attendanceManagementDto.getTrainingEndTime().split(":");
				dailyAttendanceForm.setTrainingEndTimeHour(trainingEndTime[0]);
				dailyAttendanceForm.setTrainingEndTimeMinute(trainingEndTime[1]);
			}

			if (attendanceManagementDto.getBlankTime() != null) {
				dailyAttendanceForm.setBlankTime(attendanceManagementDto.getBlankTime());
				dailyAttendanceForm.setBlankTimeValue(String.valueOf(
						attendanceUtil.calcBlankTime(attendanceManagementDto.getBlankTime())));
			}
			dailyAttendanceForm.setStatus(String.valueOf(attendanceManagementDto.getStatus()));
			dailyAttendanceForm.setNote(attendanceManagementDto.getNote());
			dailyAttendanceForm.setSectionName(attendanceManagementDto.getSectionName());
			dailyAttendanceForm.setIsToday(attendanceManagementDto.getIsToday());
			dailyAttendanceForm.setDispTrainingDate(dateUtil
					.dateToString(attendanceManagementDto.getTrainingDate(), "yyyy年M月d日(E)"));
			dailyAttendanceForm.setStatusDispName(attendanceManagementDto.getStatusDispName());

			attendanceForm.getAttendanceList().add(dailyAttendanceForm);
		}

		return attendanceForm;
	}

	/**
	 * 勤怠登録・更新処理
	 * 
	 * @param attendanceForm
	 * @return 完了メッセージ
	 * @throws ParseException
	 */
	public String update(AttendanceForm attendanceForm) throws ParseException {

		Integer lmsUserId = loginUserUtil.isStudent() ? loginUserDto.getLmsUserId()
				: attendanceForm.getLmsUserId();

		// 現在の勤怠情報（受講生入力）リストを取得
		List<TStudentAttendance> tStudentAttendanceList = tStudentAttendanceMapper
				.findByLmsUserId(lmsUserId, Constants.DB_FLG_FALSE);

		// 入力された情報を更新用のエンティティに移し替え
		Date date = new Date();
		for (DailyAttendanceForm dailyAttendanceForm : attendanceForm.getAttendanceList()) {

			// 更新用エンティティ作成
			TStudentAttendance tStudentAttendance = new TStudentAttendance();
			// 日次勤怠フォームから更新用のエンティティにコピー
			BeanUtils.copyProperties(dailyAttendanceForm, tStudentAttendance);
			// 研修日付
			tStudentAttendance
					.setTrainingDate(dateUtil.parse(dailyAttendanceForm.getTrainingDate()));
			// 現在の勤怠情報リストのうち、研修日が同じものを更新用エンティティで上書き
			for (TStudentAttendance entity : tStudentAttendanceList) {
				if (entity.getTrainingDate().equals(tStudentAttendance.getTrainingDate())) {
					tStudentAttendance = entity;
					break;
				}
			}
			tStudentAttendance.setLmsUserId(lmsUserId);
			tStudentAttendance.setAccountId(loginUserDto.getAccountId());
			// 出勤時刻整形
			TrainingTime trainingStartTime = null;
			trainingStartTime = new TrainingTime(dailyAttendanceForm.getTrainingStartTime());
			tStudentAttendance.setTrainingStartTime(trainingStartTime.getFormattedString());
			// 退勤時刻整形
			TrainingTime trainingEndTime = null;
			trainingEndTime = new TrainingTime(dailyAttendanceForm.getTrainingEndTime());
			tStudentAttendance.setTrainingEndTime(trainingEndTime.getFormattedString());
			// 中抜け時間
			tStudentAttendance.setBlankTime(dailyAttendanceForm.getBlankTime());
			// 遅刻早退ステータス
			if ((trainingStartTime != null || trainingEndTime != null)
					&& !dailyAttendanceForm.getStatusDispName().equals("欠席")) {
				AttendanceStatusEnum attendanceStatusEnum = attendanceUtil
						.getStatus(trainingStartTime, trainingEndTime);
				tStudentAttendance.setStatus(attendanceStatusEnum.code);
			}
			// 備考
			tStudentAttendance.setNote(dailyAttendanceForm.getNote());
			// 更新者と更新日時
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			// 削除フラグ
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			// 登録用Listへ追加
			tStudentAttendanceList.add(tStudentAttendance);
		}
		// 登録・更新処理
		for (TStudentAttendance tStudentAttendance : tStudentAttendanceList) {
			if (tStudentAttendance.getStudentAttendanceId() == null) {
				tStudentAttendance.setFirstCreateUser(loginUserDto.getLmsUserId());
				tStudentAttendance.setFirstCreateDate(date);
				tStudentAttendanceMapper.insert(tStudentAttendance);
			} else {
				tStudentAttendanceMapper.update(tStudentAttendance);
			}
		}
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	/**
	 * # 概要 今日より前の過去日に、未入力の勤怠があるかどうかを判定する。
	 * 
	 * @author 冨澤雄志 - Task.25
	 * @return 打刻漏れの回数の値が0より大きいか否か
	 * @throws ParseException
	 */
	public Boolean notEnterCheck() throws ParseException {

		// # 処理 
		// * 今日の日付を取得する。
		// new Date()で現在の日時を取得、"yyyy/MM/dd"の形式に成形
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd");
		Date today = sdf.parse(sdf.format(new Date()));

		// *tStudentAttendanceMapper.notEnterCount を呼び出し、未入力件数を取得する。
		Integer notEnterCount = tStudentAttendanceMapper.notEnterCount(loginUserDto.getLmsUserId(),
				Constants.DB_FLG_FALSE, today);

		// *件数が 0 より大きければ true、そうでなければ false を戻す。
		if (notEnterCount > 0) {
			return true;
		}
		return false;
	}

	/**
	 * 入力された出退勤時刻の形式を変換
	 * 
	 * @author 冨澤雄志 - Task.26
	 * @return なんか
	 * @param attendanceForm
	 */
	public void formatConversion(AttendanceForm attendanceForm) {
		/*
		 *Task.26 入力された出退勤の{時間}{分}をhh:mm形式に変換し、AttendanceFormにセットする
		
		# 概要 フォーム内の「時」と「分」の入力を、「hh:mm」形式の文字列に変換してセットする。
		
		# 処理 
		[loop] DailyAttendanceForm : フォーム内のリスト 
		* [if 出勤の「時」「分」が共に入力されている場合] %02d:%02d 形式で trainingStartTime にセットする。 
		* [if 退勤の「時」「分」が共に入力されている場合] %02d:%02d 形式で trainingEndTime にセットする。
		[loop end]
		 */
		for (DailyAttendanceForm dailyAttendanceForm : attendanceForm.getAttendanceList()) {

			// 出勤の「時」「分」が共に入力されている場合
			if (dailyAttendanceForm.getTrainingStartTimeHour() != null
					&& !dailyAttendanceForm.getTrainingStartTimeHour().isEmpty()
					&& dailyAttendanceForm.getTrainingStartTimeMinute() != null
					&& !dailyAttendanceForm.getTrainingStartTimeMinute().isEmpty()) {

				String trainingStartTime = String.format("%02d:%02d",
						Integer.parseInt(dailyAttendanceForm.getTrainingStartTimeHour()),
						Integer.parseInt(dailyAttendanceForm.getTrainingStartTimeMinute()));

				dailyAttendanceForm.setTrainingStartTime(trainingStartTime);
			}

			// 退勤の「時」「分」が共に入力されている場合
			if (dailyAttendanceForm.getTrainingEndTimeHour() != null
					&& !dailyAttendanceForm.getTrainingEndTimeHour().isEmpty()
					&& dailyAttendanceForm.getTrainingEndTimeMinute() != null
					&& !dailyAttendanceForm.getTrainingEndTimeMinute().isEmpty()) {

				String trainingEndTime = String.format("%02d:%02d",
						Integer.parseInt(dailyAttendanceForm.getTrainingEndTimeHour()),
						Integer.parseInt(dailyAttendanceForm.getTrainingEndTimeMinute()));

				dailyAttendanceForm.setTrainingEndTime(trainingEndTime);
			}
		}
	}

	/**
	 * 勤怠入力チェック
	 * 
	 * @author 冨澤雄志 - Task.27
	 * @return 
	 * @param attendanceForm
	 * @param result
	 */

	public void updateInputCheck(AttendanceForm attendanceForm, BindingResult result) {
		/* Task.27 勤怠入力チェック
		
		# 概要 勤怠更新時の入力チェックを行う（文字数、時刻の整合性、中抜け時間の妥当性）。
		
		# 処理
		[loop] DailyAttendanceForm ごとにチェックを実施。 
		    * 備考の文字数チェック（100文字以内）。 
		    * 時刻の「時」だけ、「分」だけといった片側未入力チェック。 
		    * 「出勤なし、退勤あり」の矛盾チェック。  
		    * [if エラーがなければ] 出勤時刻 ＞ 退勤時刻 になっていないか比較チェック。
		    * [if 中抜け時間が入力されている場合] 出退勤の差分から計算される最大受講時間よりも中抜け時間が長くないかチェック。
		[loop end] */

		/* 
		for (DailyAttendanceForm dailyAttendanceForm : attendanceForm.getAttendanceList()) {
			// * 備考の文字数チェック（100文字以内）。 
			if (dailyAttendanceForm.getNote() != null && dailyAttendanceForm.getNote().length() > 100) {
				// maxlength のエラーを追加
				// パラメータ：備考、100
				エラーメッセージを追加する("maxlength", "備考", "100");
			}
			// * 時刻の「時」だけ、「分」だけといった片側未入力チェック。
		
			if (!result.hasErrors()) {
				// 出勤時刻 ＞ 退勤時刻 になっていないか比較チェック。
		
			}
			if (dailyAttendanceForm.getBlankTime() != null) {
				// 出退勤の差分から計算される最大受講時間よりも中抜け時間が長くないかチェック。
		
			}
		} */
	}

}
