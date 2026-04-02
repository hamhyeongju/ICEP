import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 사용자 입력을 받아 역할별 메뉴를 운영하고,
 * 예약·체크인·관리자 기능을 포함한 전체 CLI 흐름을 조정하는 애플리케이션 본체다.
 */
class CliApp {
    private static final Pattern USER_ID_PATTERN = Pattern.compile("^[a-z][a-z0-9]{3,11}$");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^[A-Za-z0-9!@#$%^&*._-]{6,16}$");
    private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-z가-힣 ]{2,20}$");
    private static final Pattern ROOM_ID_PATTERN = Pattern.compile("^R\\d{3}$");
    private static final Pattern RESERVATION_ID_PATTERN = Pattern.compile("^rv\\d{4,}$");
    private static final Pattern EQUIPMENT_CODE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]*$");

    private final Scanner scanner = new Scanner(System.in);
    private final TextDataStore store = new TextDataStore(java.nio.file.Paths.get("").toAbsolutePath().normalize());
    private User sessionUser;

    void run() {
        System.out.println("[시스템] 가상 현재 일시 기반 스터디룸 예약·이용 관리 시스템을 시작합니다.");
        try {
            performStartupSync();
            mainLoop();
        } catch (ExitProgramException ignored) {
            // 정상 종료
        } catch (FatalAppException ignored) {
            // 치명적 오류 종료
        }
    }

    private void performStartupSync() {
        SystemDataset dataset = loadDataset(true);
        System.out.println("[안내] 현재 공용 시각은 " + TimeFormats.formatUserDateTime(dataset.currentTime) + " 입니다.");
    }

    private void mainLoop() {
        while (true) {
            if (sessionUser == null) {
                guestMenu();
            } else if (sessionUser.role == Role.MEMBER) {
                memberMenu();
            } else {
                adminMenu();
            }
        }
    }

    private void guestMenu() {
        System.out.println();
        System.out.println("[비로그인 메인 메뉴]");
        System.out.println("1. 회원가입");
        System.out.println("2. 로그인");
        System.out.println("0. 종료");
        String choice = promptMenuOption(Set.of("0", "1", "2"), "메뉴 선택: ");
        switch (choice) {
            case "1" -> handleSignUp();
            case "2" -> handleLogin();
            case "0" -> exitProgram();
            default -> throw new IllegalStateException("Unexpected value: " + choice);
        }
    }

    private void memberMenu() {
        System.out.println();
        System.out.println("[member 메인 메뉴]");
        System.out.println("1. 현재 시각 조회/변경");
        System.out.println("2. 스터디룸 목록 조회");
        System.out.println("3. 조건 기반 사용 가능 룸 검색");
        System.out.println("4. 예약 생성");
        System.out.println("5. 내 예약 조회");
        System.out.println("6. 예약 취소");
        System.out.println("7. 체크인");
        System.out.println("8. 사용 연장");
        System.out.println("9. 패널티 조회");
        System.out.println("0. 로그아웃");
        String choice = promptMenuOption(Set.of("0", "1", "2", "3", "4", "5", "6", "7", "8", "9"), "메뉴 선택: ");
        switch (choice) {
            case "1" -> handleCurrentTimeMenu();
            case "2" -> handleRoomList();
            case "3" -> handleAvailableRoomSearch();
            case "4" -> handleCreateReservation();
            case "5" -> handleMyReservations();
            case "6" -> handleCancelReservation();
            case "7" -> handleCheckIn();
            case "8" -> handleExtendReservation();
            case "9" -> handlePenaltyView();
            case "0" -> handleLogout();
            default -> throw new IllegalStateException("Unexpected value: " + choice);
        }
    }

    private void adminMenu() {
        System.out.println();
        System.out.println("[admin 메인 메뉴]");
        System.out.println("1. 현재 시각 조회/변경");
        System.out.println("2. 스터디룸 목록 조회");
        System.out.println("3. 관리자용 전체 예약 조회");
        System.out.println("4. 관리자용 사용자 패널티 조회/초기화");
        System.out.println("5. 관리자용 룸 상태 변경");
        System.out.println("0. 로그아웃");
        String choice = promptMenuOption(Set.of("0", "1", "2", "3", "4", "5"), "메뉴 선택: ");
        switch (choice) {
            case "1" -> handleCurrentTimeMenu();
            case "2" -> handleRoomList();
            case "3" -> handleAdminReservationView();
            case "4" -> handleAdminPenaltyMenu();
            case "5" -> handleRoomStatusChange();
            case "0" -> handleLogout();
            default -> throw new IllegalStateException("Unexpected value: " + choice);
        }
    }

    private void handleSignUp() {
        try {
            String userId = promptUserId("사용자 ID 입력(취소는 0): ", true);
            while (true) {
                SystemDataset dataset = loadDataset(false);
                if (dataset.users.containsKey(userId)) {
                    System.out.println("[입력 값 오류] 이미 사용 중인 사용자 ID입니다.");
                    userId = promptUserId("사용자 ID 입력(취소는 0): ", true);
                    continue;
                }
                break;
            }
            String password = promptPassword("비밀번호 입력(취소는 0): ", true);
            String name = promptName("이름 입력(취소는 0): ", true);

            while (true) {
                SystemDataset dataset = loadDataset(false);
                if (dataset.users.containsKey(userId)) {
                    System.out.println("[입력 값 오류] 이미 사용 중인 사용자 ID입니다.");
                    userId = promptUserId("사용자 ID 입력(취소는 0): ", true);
                    continue;
                }
                dataset.users.put(userId, new User(userId, password, name, Role.MEMBER, 0, UserStatus.ACTIVE, 0));
                saveDataset(dataset);
                System.out.println("[성공] 회원가입이 완료되었습니다. 로그인 후 이용해 주세요.");
                return;
            }
        } catch (CancelledActionException ignored) {
            printCancelled();
        }
    }

    private void handleLogin() {
        try {
            String userId = promptUserId("사용자 ID 입력(취소는 0): ", true);
            while (true) {
                SystemDataset dataset = loadDataset(false);
                User user = dataset.users.get(userId);
                if (user == null) {
                    System.out.println("[입력 값 오류] 존재하지 않는 사용자 ID입니다.");
                    userId = promptUserId("사용자 ID 입력(취소는 0): ", true);
                    continue;
                }
                if (user.status != UserStatus.ACTIVE) {
                    System.out.println("[입력 값 오류] 로그인할 수 없는 계정 상태입니다.");
                    userId = promptUserId("사용자 ID 입력(취소는 0): ", true);
                    continue;
                }

                while (true) {
                    String password = promptPassword("비밀번호 입력(취소는 0): ", true);
                    dataset = loadDataset(false);
                    user = dataset.users.get(userId);
                    if (user == null) {
                        System.out.println("[입력 값 오류] 존재하지 않는 사용자 ID입니다.");
                        userId = promptUserId("사용자 ID 입력(취소는 0): ", true);
                        break;
                    }
                    if (user.status != UserStatus.ACTIVE) {
                        System.out.println("[입력 값 오류] 로그인할 수 없는 계정 상태입니다.");
                        userId = promptUserId("사용자 ID 입력(취소는 0): ", true);
                        break;
                    }
                    if (!user.password.equals(password)) {
                        System.out.println("[입력 값 오류] 비밀번호가 일치하지 않습니다.");
                        continue;
                    }

                    UpdateResult result = AutoStateUpdater.apply(dataset);
                    if (result.changed()) {
                        saveDataset(dataset);
                    }
                    sessionUser = dataset.users.get(userId);
                    System.out.println("[성공] " + sessionUser.role.fileValue() + " 계정으로 로그인했습니다.");
                    return;
                }
            }
        } catch (CancelledActionException ignored) {
            printCancelled();
        }
    }

    private void handleLogout() {
        sessionUser = null;
        System.out.println("[안내] 로그아웃되었습니다.");
    }

    private void handleCurrentTimeMenu() {
        System.out.println();
        System.out.println("[현재 시각 메뉴]");
        System.out.println("1. 현재 시각 조회");
        System.out.println("2. 현재 시각 변경");
        System.out.println("0. 이전 메뉴");
        String choice = promptMenuOption(Set.of("0", "1", "2"), "메뉴 선택: ");
        switch (choice) {
            case "1" -> handleCurrentTimeView();
            case "2" -> handleCurrentTimeChange();
            case "0" -> {
                return;
            }
            default -> throw new IllegalStateException("Unexpected value: " + choice);
        }
    }

    private void handleCurrentTimeView() {
        SystemDataset dataset = loadDataset(false);
        System.out.println("[조회 결과] 현재 공용 시각은 " + TimeFormats.formatUserDateTime(dataset.currentTime) + " 입니다.");
    }

    private void handleCurrentTimeChange() {
        SystemDataset preview = loadDataset(false);
        System.out.println("현재 시각: " + TimeFormats.formatUserDateTime(preview.currentTime));
        try {
            while (true) {
                LocalDateTime newCurrentTime = promptDateTime("새 현재 시각 입력(yyyy-MM-dd HH:mm, 취소는 0): ", true);
                SystemDataset latest = loadDataset(false);
                if (newCurrentTime.isBefore(latest.currentTime)) {
                    System.out.println("[입력 값 오류] 새 현재 시각은 기존 현재 시각보다 과거일 수 없습니다.");
                    continue;
                }
                String before = TimeFormats.formatUserDateTime(latest.currentTime);
                String after = TimeFormats.formatUserDateTime(newCurrentTime);
                boolean timeChanged = !newCurrentTime.equals(latest.currentTime);
                latest.currentTime = newCurrentTime;
                UpdateResult result = AutoStateUpdater.apply(latest);
                if (timeChanged || result.changed()) {
                    saveDataset(latest);
                }
                if (timeChanged) {
                    System.out.println("[성공] 현재 시각이 " + before + "에서 " + after + "로 변경되었습니다.");
                } else {
                    System.out.println("[성공] 현재 시각이 " + before + "로 유지되었습니다.");
                }
                System.out.println("[안내] 자동 상태 갱신 결과: " + latest.summarizeAutoUpdate(result));
                return;
            }
        } catch (CancelledActionException ignored) {
            printCancelled();
        }
    }

    private void handleRoomList() {
        SystemDataset dataset = loadDataset(false);
        List<Room> rooms = dataset.sortedRooms();
        if (rooms.isEmpty()) {
            System.out.println("[조회 결과] 등록된 스터디룸이 없습니다.");
            return;
        }
        System.out.println("[조회 결과] 총 " + rooms.size() + "개의 스터디룸이 있습니다.");
        for (Room room : rooms) {
            System.out.println(formatRoom(room));
        }
    }

    private void handleAvailableRoomSearch() {
        SystemDataset dataset = loadDataset(true);
        System.out.println("[안내] 현재 시각: " + TimeFormats.formatUserDateTime(dataset.currentTime));
        try {
            int headCount = promptHeadCount("사용 인원 입력(1~20, 취소는 0): ", true);
            LocalDateTime start = promptReservationStart(dataset, "예약 시작 시각 입력(yyyy-MM-dd HH:mm, 취소는 0): ", true);
            LocalDateTime end = promptReservationEnd(start, "예약 종료 시각 입력(yyyy-MM-dd HH:mm, 취소는 0): ", true);
            LinkedHashSet<String> requiredEquipment = promptEquipmentList("필요 비품 입력(- 또는 CODE+CODE, 취소는 0): ", true);

            List<Room> availableRooms = new ArrayList<>();
            for (Room room : dataset.sortedRooms()) {
                if (room.status != RoomStatus.OPEN) {
                    continue;
                }
                if (room.capacity < headCount) {
                    continue;
                }
                if (!room.hasAllEquipment(requiredEquipment)) {
                    continue;
                }
                if (!fitsRoomSchedule(room, start, end)) {
                    continue;
                }
                if (hasRoomConflict(dataset, room.roomId, start, end, null)) {
                    continue;
                }
                availableRooms.add(room);
            }

            if (availableRooms.isEmpty()) {
                System.out.println("[조회 결과] 입력 조건을 만족하는 룸이 없습니다.");
                return;
            }
            System.out.println("[조회 결과] 입력 조건을 만족하는 룸은 " + availableRooms.size() + "개입니다.");
            for (Room room : availableRooms) {
                System.out.println(formatRoom(room));
            }
        } catch (CancelledActionException ignored) {
            printCancelled();
        }
    }

    private void handleCreateReservation() {
        SystemDataset preview = loadDataset(true);
        System.out.println("[안내] 현재 시각: " + TimeFormats.formatUserDateTime(preview.currentTime));
        try {
            while (true) {
                SystemDataset dataset = loadDataset(true);
                User currentUser = currentMember(dataset);
                if (currentUser.penalty >= 2) {
                    System.out.println("[입력 값 오류] 현재 패널티가 2점 이상이어서 새로운 예약을 생성할 수 없습니다.");
                    return;
                }
                if (countFutureReservations(dataset, currentUser.userId) >= 2) {
                    System.out.println("[입력 값 오류] 미래 예약은 최대 2개까지만 가질 수 있습니다.");
                    return;
                }

                String roomId;
                Room room;
                while (true) {
                    roomId = promptRoomId("룸 ID 입력(취소는 0): ", true);
                    room = dataset.rooms.get(roomId);
                    if (room == null) {
                        System.out.println("[입력 값 오류] 존재하지 않는 룸 ID입니다.");
                        continue;
                    }
                    if (room.status != RoomStatus.OPEN) {
                        System.out.println("[입력 값 오류] 현재 OPEN 상태의 룸만 예약할 수 있습니다.");
                        continue;
                    }
                    break;
                }

                int headCount;
                while (true) {
                    headCount = promptHeadCount("사용 인원 입력(1~20, 취소는 0): ", true);
                    if (room.capacity < headCount) {
                        System.out.println("[입력 값 오류] 사용 인원이 룸 수용 인원을 초과했습니다.");
                        continue;
                    }
                    break;
                }

                while (true) {
                    LocalDateTime start = promptReservationStart(dataset, "예약 시작 시각 입력(yyyy-MM-dd HH:mm, 취소는 0): ", true);
                    LocalDateTime end = promptReservationEnd(start, "예약 종료 시각 입력(yyyy-MM-dd HH:mm, 취소는 0): ", true);

                    if (!fitsRoomSchedule(room, start, end)) {
                        System.out.println("[입력 값 오류] 예약 시각이 룸 운영 시간을 벗어납니다.");
                        continue;
                    }
                    if (hasRoomConflict(dataset, room.roomId, start, end, null)) {
                        System.out.println("[입력 값 오류] 같은 룸에 겹치는 예약이 이미 존재합니다.");
                        continue;
                    }
                    if (hasUserConflict(dataset, currentUser.userId, start, end, null)) {
                        System.out.println("[입력 값 오류] 같은 시간대의 본인 예약이 이미 존재합니다.");
                        continue;
                    }

                    SystemDataset latest = loadDataset(true);
                    User latestUser = currentMember(latest);
                    if (latestUser.penalty >= 2) {
                        System.out.println("[입력 값 오류] 현재 패널티가 2점 이상이어서 새로운 예약을 생성할 수 없습니다.");
                        return;
                    }
                    if (countFutureReservations(latest, latestUser.userId) >= 2) {
                        System.out.println("[입력 값 오류] 미래 예약은 최대 2개까지만 가질 수 있습니다.");
                        return;
                    }

                    Room latestRoom = latest.rooms.get(room.roomId);
                    if (latestRoom == null) {
                        System.out.println("[입력 값 오류] 존재하지 않는 룸 ID입니다.");
                        break;
                    }
                    if (latestRoom.status != RoomStatus.OPEN) {
                        System.out.println("[입력 값 오류] 현재 OPEN 상태의 룸만 예약할 수 있습니다.");
                        break;
                    }
                    if (latestRoom.capacity < headCount) {
                        System.out.println("[입력 값 오류] 사용 인원이 룸 수용 인원을 초과했습니다.");
                        break;
                    }
                    String error = validateReservationWindow(latest, start, end);
                    if (error != null) {
                        System.out.println("[입력 값 오류] " + error);
                        continue;
                    }
                    if (!fitsRoomSchedule(latestRoom, start, end)) {
                        System.out.println("[입력 값 오류] 예약 시각이 룸 운영 시간을 벗어납니다.");
                        continue;
                    }
                    if (hasRoomConflict(latest, latestRoom.roomId, start, end, null)) {
                        System.out.println("[입력 값 오류] 같은 룸에 겹치는 예약이 이미 존재합니다.");
                        continue;
                    }
                    if (hasUserConflict(latest, latestUser.userId, start, end, null)) {
                        System.out.println("[입력 값 오류] 같은 시간대의 본인 예약이 이미 존재합니다.");
                        continue;
                    }

                    String reservationId = latest.nextReservationId();
                    latest.reservations.put(reservationId, new Reservation(
                            reservationId,
                            latestRoom.roomId,
                            latestUser.userId,
                            start.toLocalDate(),
                            start.toLocalTime(),
                            end.toLocalTime(),
                            ReservationStatus.RESERVED,
                            null,
                            0,
                            0));
                    saveDataset(latest);
                    System.out.println("[성공] 예약이 생성되었습니다. 예약 ID는 " + reservationId + "입니다.");
                    return;
                }
            }
        } catch (CancelledActionException ignored) {
            printCancelled();
        }
    }

    private void handleMyReservations() {
        SystemDataset dataset = loadDataset(true);
        User currentUser = currentMember(dataset);
        List<Reservation> mine = new ArrayList<>();
        for (Reservation reservation : dataset.sortedReservations()) {
            if (reservation.userId.equals(currentUser.userId)) {
                mine.add(reservation);
            }
        }
        if (mine.isEmpty()) {
            System.out.println("[조회 결과] 내 예약이 없습니다.");
            return;
        }
        System.out.println("[조회 결과] 내 예약은 총 " + mine.size() + "건입니다.");
        for (Reservation reservation : mine) {
            System.out.println(formatMemberReservation(reservation));
        }
    }

    private void handleCancelReservation() {
        try {
            while (true) {
                String reservationId = promptReservationId("예약 ID 입력(취소는 0): ", true);
                SystemDataset dataset = loadDataset(true);
                User currentUser = currentMember(dataset);
                Reservation reservation = dataset.reservations.get(reservationId);
                if (reservation == null) {
                    System.out.println("[입력 값 오류] 존재하지 않는 예약 ID입니다.");
                    continue;
                }
                if (!reservation.userId.equals(currentUser.userId)) {
                    System.out.println("[입력 값 오류] 본인 예약만 취소할 수 있습니다.");
                    continue;
                }
                if (reservation.status != ReservationStatus.RESERVED) {
                    System.out.println("[입력 값 오류] RESERVED 상태의 예약만 취소할 수 있습니다.");
                    continue;
                }

                boolean penalty = dataset.currentTime.isAfter(reservation.startDateTime().minusMinutes(30));
                reservation.status = ReservationStatus.CANCELLED;
                reservation.checkedInAt = null;
                if (penalty) {
                    currentUser.penalty += 1;
                }
                saveDataset(dataset);
                if (penalty) {
                    System.out.println("[성공] 예약이 취소되었습니다. 지연 취소로 패널티 1점이 부여되었습니다.");
                } else {
                    System.out.println("[성공] 예약이 취소되었습니다. 패널티는 부여되지 않았습니다.");
                }
                return;
            }
        } catch (CancelledActionException ignored) {
            printCancelled();
        }
    }

    private void handleCheckIn() {
        try {
            while (true) {
                String reservationId = promptReservationId("예약 ID 입력(취소는 0): ", true);
                SystemDataset dataset = loadDataset(true);
                User currentUser = currentMember(dataset);
                Reservation reservation = dataset.reservations.get(reservationId);
                if (reservation == null) {
                    System.out.println("[입력 값 오류] 존재하지 않는 예약 ID입니다.");
                    continue;
                }
                if (!reservation.userId.equals(currentUser.userId)) {
                    System.out.println("[입력 값 오류] 본인 예약만 체크인할 수 있습니다.");
                    continue;
                }
                if (reservation.status != ReservationStatus.RESERVED) {
                    System.out.println("[입력 값 오류] RESERVED 상태의 예약만 체크인할 수 있습니다.");
                    continue;
                }
                LocalDateTime start = reservation.startDateTime();
                LocalDateTime now = dataset.currentTime;
                if (now.isBefore(start.minusMinutes(10)) || now.isAfter(start.plusMinutes(15))) {
                    System.out.println("[입력 값 오류] 체크인 가능 시간은 예약 시작 10분 전부터 시작 후 15분까지입니다.");
                    continue;
                }
                Room room = dataset.rooms.get(reservation.roomId);
                if (room == null || room.status != RoomStatus.OPEN) {
                    System.out.println("[입력 값 오류] 현재 OPEN 상태의 룸에서만 체크인할 수 있습니다.");
                    continue;
                }
                reservation.status = ReservationStatus.CHECKED_IN;
                reservation.checkedInAt = now;
                saveDataset(dataset);
                System.out.println("[성공] 체크인이 완료되었습니다.");
                return;
            }
        } catch (CancelledActionException ignored) {
            printCancelled();
        }
    }

    private void handleExtendReservation() {
        try {
            while (true) {
                String reservationId = promptReservationId("예약 ID 입력(취소는 0): ", true);
                SystemDataset dataset = loadDataset(true);
                User currentUser = currentMember(dataset);
                Reservation reservation = dataset.reservations.get(reservationId);
                if (reservation == null) {
                    System.out.println("[입력 값 오류] 존재하지 않는 예약 ID입니다.");
                    continue;
                }
                if (!reservation.userId.equals(currentUser.userId)) {
                    System.out.println("[입력 값 오류] 본인 예약만 연장할 수 있습니다.");
                    continue;
                }
                if (reservation.status != ReservationStatus.CHECKED_IN) {
                    System.out.println("[입력 값 오류] CHECKED_IN 상태의 예약만 연장할 수 있습니다.");
                    continue;
                }
                if (reservation.extensionCount != 0) {
                    System.out.println("[입력 값 오류] 연장은 1회만 허용됩니다.");
                    continue;
                }
                Room room = dataset.rooms.get(reservation.roomId);
                if (room == null || room.status != RoomStatus.OPEN) {
                    System.out.println("[입력 값 오류] 현재 OPEN 상태의 룸에서만 연장할 수 있습니다.");
                    continue;
                }
                java.time.LocalTime newEndTime = reservation.endTime.plusMinutes(30);
                if (newEndTime.isAfter(room.closeTime)) {
                    System.out.println("[입력 값 오류] 연장 시 룸 운영 종료 시각을 넘을 수 없습니다.");
                    continue;
                }
                LocalDateTime newEnd = LocalDateTime.of(reservation.date, newEndTime);
                if (hasRoomConflict(dataset, reservation.roomId, reservation.startDateTime(), newEnd, reservation.reservationId)) {
                    System.out.println("[입력 값 오류] 뒤 예약이 있어 연장할 수 없습니다.");
                    continue;
                }
                reservation.endTime = newEndTime;
                reservation.extensionCount = 1;
                saveDataset(dataset);
                System.out.println("[성공] 예약 종료 시각이 30분 연장되었습니다.");
                return;
            }
        } catch (CancelledActionException ignored) {
            printCancelled();
        }
    }

    private void handlePenaltyView() {
        SystemDataset dataset = loadDataset(true);
        User currentUser = currentMember(dataset);
        String availability = currentUser.penalty >= 2 ? "새로운 예약을 생성할 수 없습니다." : "새로운 예약을 생성할 수 있습니다.";
        System.out.println("[조회 결과] 현재 패널티는 " + currentUser.penalty + "점이며 " + availability);
    }

    private void handleAdminReservationView() {
        SystemDataset dataset = loadDataset(true);
        List<Reservation> reservations = dataset.sortedReservations();
        if (reservations.isEmpty()) {
            System.out.println("[조회 결과] 전체 예약이 없습니다.");
            return;
        }
        System.out.println("[조회 결과] 전체 예약 " + reservations.size() + "건을 표시합니다.");
        for (Reservation reservation : reservations) {
            System.out.println(formatAdminReservation(reservation));
        }
    }

    private void handleAdminPenaltyMenu() {
        System.out.println();
        System.out.println("[관리자용 사용자 패널티 메뉴]");
        System.out.println("1. 전체 member 패널티 조회");
        System.out.println("2. 특정 member 패널티 초기화");
        System.out.println("0. 이전 메뉴");
        String choice = promptMenuOption(Set.of("0", "1", "2"), "메뉴 선택: ");
        switch (choice) {
            case "1" -> handleAdminPenaltyView();
            case "2" -> handleAdminPenaltyReset();
            case "0" -> {
                return;
            }
            default -> throw new IllegalStateException("Unexpected value: " + choice);
        }
    }

    private void handleAdminPenaltyView() {
        SystemDataset dataset = loadDataset(true);
        List<User> members = new ArrayList<>();
        for (User user : dataset.sortedUsers()) {
            if (user.role == Role.MEMBER) {
                members.add(user);
            }
        }
        if (members.isEmpty()) {
            System.out.println("[조회 결과] 조회할 member 계정이 없습니다.");
            return;
        }
        System.out.println("[조회 결과] member 패널티 목록입니다.");
        for (User member : members) {
            String availability = member.penalty >= 2 ? "예약 생성 불가" : "예약 생성 가능";
            System.out.println(member.userId + " | 이름 " + member.name + " | 패널티 " + member.penalty + "점 | " + availability);
        }
    }

    private void handleAdminPenaltyReset() {
        try {
            while (true) {
                String userId = promptUserId("초기화할 member 사용자 ID 입력(취소는 0): ", true);
                SystemDataset dataset = loadDataset(true);
                User target = dataset.users.get(userId);
                if (target == null) {
                    System.out.println("[입력 값 오류] 존재하지 않는 사용자 ID입니다.");
                    continue;
                }
                if (target.role != Role.MEMBER) {
                    System.out.println("[입력 값 오류] member 계정만 패널티를 초기화할 수 있습니다.");
                    continue;
                }
                if (target.penalty == 0) {
                    System.out.println("[입력 값 오류] 이미 패널티가 0점입니다.");
                    continue;
                }
                target.penalty = 0;
                saveDataset(dataset);
                System.out.println("[성공] " + target.userId + "의 패널티가 0점으로 초기화되었습니다.");
                return;
            }
        } catch (CancelledActionException ignored) {
            printCancelled();
        }
    }

    private void handleRoomStatusChange() {
        try {
            while (true) {
                String roomId = promptRoomId("룸 ID 입력(취소는 0): ", true);
                SystemDataset dataset = loadDataset(true);
                Room room = dataset.rooms.get(roomId);
                if (room == null) {
                    System.out.println("[입력 값 오류] 존재하지 않는 룸 ID입니다.");
                    continue;
                }

                while (true) {
                    RoomStatus newStatus = promptRoomStatus("목표 상태 입력(OPEN/CLOSED/MAINTENANCE, 취소는 0): ", true);
                    if (room.status == newStatus) {
                        System.out.println("[입력 값 오류] 현재 상태와 동일한 상태로는 변경할 수 없습니다.");
                        continue;
                    }
                    if (newStatus != RoomStatus.OPEN && hasOpenEndedReservation(dataset, room.roomId)) {
                        System.out.println("[입력 값 오류] 현재 또는 미래의 활성 예약이 남아 있어 CLOSED/MAINTENANCE로 변경할 수 없습니다.");
                        break;
                    }
                    RoomStatus before = room.status;
                    room.status = newStatus;
                    saveDataset(dataset);
                    System.out.println("[성공] " + room.roomId + "의 운영 상태가 " + before.fileValue() + "에서 " + newStatus.fileValue() + "로 변경되었습니다.");
                    return;
                }
            }
        } catch (CancelledActionException ignored) {
            printCancelled();
        }
    }

    private boolean hasOpenEndedReservation(SystemDataset dataset, String roomId) {
        for (Reservation reservation : dataset.reservations.values()) {
            if (!reservation.roomId.equals(roomId)) {
                continue;
            }
            if (!reservation.isActiveForConflict()) {
                continue;
            }
            if (reservation.endDateTime().isAfter(dataset.currentTime)) {
                return true;
            }
        }
        return false;
    }

    private User currentMember(SystemDataset dataset) {
        syncSessionUser(dataset);
        if (sessionUser == null || sessionUser.role != Role.MEMBER) {
            System.out.println("[권한 오류] member 전용 기능입니다.");
            throw new FatalAppException();
        }
        return sessionUser;
    }

    private String validateReservationStart(SystemDataset dataset, LocalDateTime start) {
        if (!isHalfHour(start)) {
            return "예약 시각은 30분 단위여야 합니다.";
        }
        if (!start.isAfter(dataset.currentTime)) {
            return "예약 시작 시각은 현재 시각보다 늦어야 합니다.";
        }
        if (start.isAfter(dataset.currentTime.plusDays(14))) {
            return "예약 시작 시각은 현재 시각 기준 14일 이내여야 합니다.";
        }
        return null;
    }

    private String validateReservationEnd(LocalDateTime start, LocalDateTime end) {
        if (!start.toLocalDate().equals(end.toLocalDate())) {
            return "예약은 같은 날짜 안에서만 생성할 수 있습니다.";
        }
        if (!isHalfHour(end)) {
            return "예약 시각은 30분 단위여야 합니다.";
        }
        if (!end.isAfter(start)) {
            return "예약 종료 시각은 시작 시각보다 늦어야 합니다.";
        }
        long duration = Duration.between(start, end).toMinutes();
        if (duration % 30 != 0) {
            return "예약 길이는 30분 단위여야 합니다.";
        }
        if (duration < 60 || duration > 240) {
            return "예약 길이는 1시간 이상 4시간 이하이어야 합니다.";
        }
        return null;
    }

    private String validateReservationWindow(SystemDataset dataset, LocalDateTime start, LocalDateTime end) {
        String startError = validateReservationStart(dataset, start);
        if (startError != null) {
            return startError;
        }
        return validateReservationEnd(start, end);
    }

    private boolean fitsRoomSchedule(Room room, LocalDateTime start, LocalDateTime end) {
        return !start.toLocalTime().isBefore(room.openTime) && !end.toLocalTime().isAfter(room.closeTime);
    }

    private boolean hasRoomConflict(SystemDataset dataset, String roomId, LocalDateTime start, LocalDateTime end, String excludeReservationId) {
        for (Reservation reservation : dataset.reservations.values()) {
            if (!reservation.roomId.equals(roomId)) {
                continue;
            }
            if (excludeReservationId != null && excludeReservationId.equals(reservation.reservationId)) {
                continue;
            }
            if (!reservation.isActiveForConflict()) {
                continue;
            }
            if (overlaps(start, end, reservation.startDateTime(), reservation.endDateTime())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasUserConflict(SystemDataset dataset, String userId, LocalDateTime start, LocalDateTime end, String excludeReservationId) {
        for (Reservation reservation : dataset.reservations.values()) {
            if (!reservation.userId.equals(userId)) {
                continue;
            }
            if (excludeReservationId != null && excludeReservationId.equals(reservation.reservationId)) {
                continue;
            }
            if (!reservation.isActiveForConflict()) {
                continue;
            }
            if (overlaps(start, end, reservation.startDateTime(), reservation.endDateTime())) {
                return true;
            }
        }
        return false;
    }

    private int countFutureReservations(SystemDataset dataset, String userId) {
        int count = 0;
        for (Reservation reservation : dataset.reservations.values()) {
            if (reservation.userId.equals(userId) && reservation.isFutureReserved(dataset.currentTime)) {
                count++;
            }
        }
        return count;
    }

    private boolean overlaps(LocalDateTime leftStart, LocalDateTime leftEnd, LocalDateTime rightStart, LocalDateTime rightEnd) {
        return leftStart.isBefore(rightEnd) && rightStart.isBefore(leftEnd);
    }

    private boolean isHalfHour(LocalDateTime value) {
        return value.getMinute() == 0 || value.getMinute() == 30;
    }

    private LocalDateTime promptReservationStart(SystemDataset dataset, String prompt, boolean cancellable) throws CancelledActionException {
        while (true) {
            LocalDateTime start = promptDateTime(prompt, cancellable);
            String error = validateReservationStart(dataset, start);
            if (error != null) {
                System.out.println("[입력 값 오류] " + error);
                continue;
            }
            return start;
        }
    }

    private LocalDateTime promptReservationEnd(LocalDateTime start, String prompt, boolean cancellable) throws CancelledActionException {
        while (true) {
            LocalDateTime end = promptDateTime(prompt, cancellable);
            String error = validateReservationEnd(start, end);
            if (error != null) {
                System.out.println("[입력 값 오류] " + error);
                continue;
            }
            return end;
        }
    }

    private void saveDataset(SystemDataset dataset) {
        try {
            store.saveAll(dataset);
            syncSessionUser(dataset);
        } catch (AppDataException e) {
            terminateDueToDataError(e);
        }
    }

    private SystemDataset loadDataset(boolean autoUpdate) {
        try {
            SystemDataset dataset = store.loadAll();
            if (autoUpdate) {
                UpdateResult result = AutoStateUpdater.apply(dataset);
                if (result.changed()) {
                    store.saveAll(dataset);
                }
            }
            syncSessionUser(dataset);
            return dataset;
        } catch (AppDataException e) {
            terminateDueToDataError(e);
            throw new FatalAppException();
        }
    }

    private void syncSessionUser(SystemDataset dataset) {
        if (sessionUser == null) {
            return;
        }
        User refreshed = dataset.users.get(sessionUser.userId);
        if (refreshed == null || refreshed.role != sessionUser.role || refreshed.status != UserStatus.ACTIVE) {
            System.out.println("[세션 오류] 현재 로그인 계정 정보가 데이터 파일과 일치하지 않아 프로그램을 종료합니다.");
            throw new FatalAppException();
        }
        sessionUser = refreshed;
    }

    private void terminateDueToDataError(AppDataException e) {
        if (e.getLineNumber() > 0) {
            System.out.println("[파일 오류] " + e.getFileName() + " " + e.getLineNumber() + "행: " + e.getMessage());
        } else {
            System.out.println("[파일 오류] " + e.getFileName() + ": " + e.getMessage());
        }
        System.out.println("[안내] 데이터 오류로 프로그램을 종료합니다.");
        throw new FatalAppException();
    }

    private String formatRoom(Room room) {
        return room.roomId + " | 수용 " + room.capacity + "명 | 비품 " + room.equipmentDisplay()
                + " | 상태 " + room.status.fileValue()
                + " | 운영 " + TimeFormats.formatUserTime(room.openTime) + "-" + TimeFormats.formatUserTime(room.closeTime);
    }

    private String formatMemberReservation(Reservation reservation) {
        return reservation.reservationId
                + " | 룸 " + reservation.roomId
                + " | " + TimeFormats.formatUserDate(reservation.date)
                + " " + TimeFormats.formatUserTime(reservation.startTime)
                + "~" + TimeFormats.formatUserTime(reservation.endTime)
                + " | 상태 " + reservation.status.fileValue()
                + " | 체크인 " + (reservation.checkedInAt == null ? "-" : TimeFormats.formatUserDateTime(reservation.checkedInAt))
                + " | 연장 " + reservation.extensionCount + "회";
    }

    private String formatAdminReservation(Reservation reservation) {
        return reservation.reservationId
                + " | 사용자 " + reservation.userId
                + " | 룸 " + reservation.roomId
                + " | " + TimeFormats.formatUserDate(reservation.date)
                + " " + TimeFormats.formatUserTime(reservation.startTime)
                + "~" + TimeFormats.formatUserTime(reservation.endTime)
                + " | 상태 " + reservation.status.fileValue()
                + " | 체크인 " + (reservation.checkedInAt == null ? "-" : TimeFormats.formatUserDateTime(reservation.checkedInAt))
                + " | 연장 " + reservation.extensionCount + "회";
    }

    private String promptMenuOption(Set<String> allowedOptions, String prompt) {
        while (true) {
            String value = readTrimmedLine(prompt);
            if (value.isEmpty()) {
                System.out.println("[입력 형식 오류] 메뉴 번호만 입력해야 합니다.");
                continue;
            }
            if (!allowedOptions.contains(value)) {
                if (value.chars().allMatch(Character::isDigit)) {
                    System.out.println("[입력 값 오류] 현재 메뉴에서 선택할 수 없는 번호입니다.");
                } else {
                    System.out.println("[입력 형식 오류] 메뉴 번호만 입력해야 합니다.");
                }
                continue;
            }
            return value;
        }
    }

    private String promptUserId(String prompt, boolean cancellable) throws CancelledActionException {
        while (true) {
            String value = promptLine(prompt, cancellable);
            if (!USER_ID_PATTERN.matcher(value).matches()) {
                System.out.println("[입력 형식 오류] 사용자 ID는 영문 소문자로 시작하는 4~12자여야 합니다.");
                continue;
            }
            return value;
        }
    }

    private String promptPassword(String prompt, boolean cancellable) throws CancelledActionException {
        while (true) {
            String value = promptLine(prompt, cancellable);
            if (!PASSWORD_PATTERN.matcher(value).matches()) {
                System.out.println("[입력 형식 오류] 비밀번호는 6~16자의 영문, 숫자, 지정 특수문자만 사용할 수 있습니다.");
                continue;
            }
            return value;
        }
    }

    private String promptName(String prompt, boolean cancellable) throws CancelledActionException {
        while (true) {
            String value = promptLine(prompt, cancellable);
            if (!NAME_PATTERN.matcher(value).matches()) {
                System.out.println("[입력 형식 오류] 이름은 2~20자의 한글 또는 영문으로 입력해야 합니다.");
                continue;
            }
            if (value.contains("  ")) {
                System.out.println("[입력 값 오류] 이름에는 연속 공백을 사용할 수 없습니다.");
                continue;
            }
            if (value.replace(" ", "").length() < 2) {
                System.out.println("[입력 값 오류] 이름은 공백을 제외한 실제 문자가 2자 이상이어야 합니다.");
                continue;
            }
            return value;
        }
    }

    private String promptRoomId(String prompt, boolean cancellable) throws CancelledActionException {
        while (true) {
            String value = promptLine(prompt, cancellable);
            if (!ROOM_ID_PATTERN.matcher(value).matches()) {
                System.out.println("[입력 형식 오류] 룸 ID는 R101 형식으로 입력해야 합니다.");
                continue;
            }
            return value;
        }
    }

    private String promptReservationId(String prompt, boolean cancellable) throws CancelledActionException {
        while (true) {
            String value = promptLine(prompt, cancellable);
            if (!RESERVATION_ID_PATTERN.matcher(value).matches()) {
                System.out.println("[입력 형식 오류] 예약 ID는 rv0001 형식으로 입력해야 합니다.");
                continue;
            }
            return value;
        }
    }

    private int promptHeadCount(String prompt, boolean cancellable) throws CancelledActionException {
        while (true) {
            String value = promptLine(prompt, cancellable);
            try {
                int parsed = Integer.parseInt(value);
                if (parsed < 1 || parsed > 20) {
                    System.out.println("[입력 값 오류] 사용 인원은 1명 이상 20명 이하이어야 합니다.");
                    continue;
                }
                return parsed;
            } catch (NumberFormatException e) {
                System.out.println("[입력 형식 오류] 사용 인원은 정수로 입력해야 합니다.");
            }
        }
    }

    private LocalDateTime promptDateTime(String prompt, boolean cancellable) throws CancelledActionException {
        while (true) {
            String value = promptLine(prompt, cancellable);
            try {
                return LocalDateTime.parse(value, TimeFormats.INPUT_DATE_TIME);
            } catch (DateTimeParseException e) {
                System.out.println("[입력 형식 오류] 날짜/시각은 yyyy-MM-dd HH:mm 형식으로 입력해야 합니다.");
            }
        }
    }

    private LinkedHashSet<String> promptEquipmentList(String prompt, boolean cancellable) throws CancelledActionException {
        while (true) {
            String value = promptLine(prompt, cancellable);
            if ("-".equals(value)) {
                return new LinkedHashSet<>();
            }
            String[] parts = value.split("\\+");
            LinkedHashSet<String> result = new LinkedHashSet<>();
            boolean valid = true;
            for (String part : parts) {
                if (!EQUIPMENT_CODE_PATTERN.matcher(part).matches()) {
                    valid = false;
                    break;
                }
                result.add(part);
            }
            if (!valid || result.size() != parts.length) {
                System.out.println("[입력 형식 오류] 비품은 - 또는 PROJECTOR+WHITEBOARD 형식으로 입력해야 합니다.");
                continue;
            }
            return result;
        }
    }

    private RoomStatus promptRoomStatus(String prompt, boolean cancellable) throws CancelledActionException {
        while (true) {
            String value = promptLine(prompt, cancellable);
            switch (value) {
                case "OPEN" -> {
                    return RoomStatus.OPEN;
                }
                case "CLOSED" -> {
                    return RoomStatus.CLOSED;
                }
                case "MAINTENANCE" -> {
                    return RoomStatus.MAINTENANCE;
                }
                default -> System.out.println("[입력 형식 오류] 상태는 OPEN, CLOSED, MAINTENANCE 중 하나여야 합니다.");
            }
        }
    }

    private String promptLine(String prompt, boolean cancellable) throws CancelledActionException {
        while (true) {
            String value = readTrimmedLine(prompt);
            if (cancellable && "0".equals(value)) {
                throw new CancelledActionException();
            }
            if (value.isEmpty()) {
                System.out.println("[입력 형식 오류] 빈 입력은 허용되지 않습니다.");
                continue;
            }
            return value;
        }
    }

    private String readTrimmedLine(String prompt) {
        System.out.print(prompt);
        if (!scanner.hasNextLine()) {
            System.out.println();
            System.out.println("[안내] 입력이 종료되어 프로그램을 종료합니다.");
            throw new ExitProgramException();
        }
        return scanner.nextLine().strip();
    }

    private void printCancelled() {
        System.out.println("[안내] 현재 작업을 취소했습니다.");
    }

    private void exitProgram() {
        System.out.println("[안내] 프로그램을 종료합니다.");
        throw new ExitProgramException();
    }
}
