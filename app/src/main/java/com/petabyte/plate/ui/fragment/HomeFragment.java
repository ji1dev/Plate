package com.petabyte.plate.ui.fragment;

import android.app.ActivityOptions;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.airbnb.lottie.LottieAnimationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.petabyte.plate.R;
import com.petabyte.plate.data.DiningMasterData;
import com.petabyte.plate.data.FoodStyle;
import com.petabyte.plate.data.HomeAwardsData;
import com.petabyte.plate.data.HomeCardData;
import com.petabyte.plate.data.ImageSlideData;
import com.petabyte.plate.ui.activity.SearchActivity;
import com.petabyte.plate.ui.view.HomeAwardsList;
import com.petabyte.plate.ui.view.HomeHorizontalList;
import com.petabyte.plate.ui.view.HomeTodayFood;
import com.petabyte.plate.utils.ConnectionCodes;
import com.petabyte.plate.utils.KoreanUtil;
import com.petabyte.plate.utils.LogTags;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class HomeFragment extends Fragment implements SwipeRefreshLayout.OnRefreshListener {

    public static final int LIST_COUNT = 7;
    public static int current;
    private boolean[] completeLoaded;

    private CardView searchButton;
    private CardView applyCardView;
    private CardView addDiningPlanCardView;
    private ImageView applyImage;
    private HomeHorizontalList recentList;
    private HomeHorizontalList foodTypeList;
    private HomeHorizontalList[] chipTypeList;
    private HomeAwardsList postList;
    private HomeAwardsList imageSlider;
    private LottieAnimationView loadingSkeletonView;
    private HomeTodayFood todayFood;

    private SwipeRefreshLayout swipeRefreshLayout;
    private LinearLayout mainLayout;
    private NestedScrollView scrollView;

    private DatabaseReference mDatabase;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_home, container, false);

        // Firebase 초기화
        mDatabase = FirebaseDatabase.getInstance().getReference();

        // chipTypeList 배열 초기화
        chipTypeList = new HomeHorizontalList[2];

        initCompleteLoadedArray();

        // 뷰를 불러온다.
        searchButton = (CardView)v.findViewById(R.id.search_card_fm_home);
        applyCardView = (CardView)v.findViewById(R.id.apply_cv_fm_home);
        addDiningPlanCardView = (CardView)v.findViewById(R.id.add_dining_cv_fm_home);
        applyImage = (ImageView)v.findViewById(R.id.apply_iv_fm_home);
        recentList = (HomeHorizontalList)v.findViewById(R.id.recent_hh_fm_home);
        postList = (HomeAwardsList)v.findViewById(R.id.awards_ha_fm_home);
        imageSlider = (HomeAwardsList)v.findViewById(R.id.image_slide_fm_home);
        swipeRefreshLayout = (SwipeRefreshLayout)v.findViewById(R.id.swipe_layout_fm_home);
        mainLayout = (LinearLayout) v.findViewById(R.id.main_layout_fm_home);
        scrollView = (NestedScrollView)v.findViewById(R.id.scroll_v_fm_home);
        foodTypeList = (HomeHorizontalList)v.findViewById(R.id.food_type_hh_fm_home);
        chipTypeList[0] = (HomeHorizontalList)v.findViewById(R.id.chip_type_hh_fm_home);
        chipTypeList[1] = (HomeHorizontalList)v.findViewById(R.id.chip_type_2_hh_fm_home);
        loadingSkeletonView = (LottieAnimationView)v.findViewById(R.id.loading_lottie_fm_home);
        todayFood = (HomeTodayFood)v.findViewById(R.id.today_food_fm_home);

        // 처음에 SwipeRefreshLayout을 숨기고 로딩을 보여준다.
        scrollView.setVisibility(View.GONE);
        loadingSkeletonView.setVisibility(View.VISIBLE);
        searchButton.setVisibility(View.GONE);

        // 스와이프 시 Listener 설정
        swipeRefreshLayout.setOnRefreshListener(this);

        // 검색으로 이동하기 위한 검색창 클릭 리스너
        searchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getActivity(), SearchActivity.class);

                // 지금 돌리는 프로그램이 안드로이드 롤리팝(5.0)인지 확인
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    ActivityOptions options = ActivityOptions.makeSceneTransitionAnimation(getActivity(), searchButton, "Search");
                    Objects.requireNonNull(getActivity()).startActivityForResult(intent, ConnectionCodes.REQUEST_SEARCH_ACTIVITY, options.toBundle());
                } else
                    // 롤리팝 이하면 애니메이션 없이 액티비티 호출
                    Objects.requireNonNull(getActivity()).startActivityForResult(intent, ConnectionCodes.REQUEST_SEARCH_ACTIVITY);
            }
        });

        // 다이닝 추가와 관련된 클릭 리스너
        addDiningPlanCardView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Start AddDiningPlanActivity here..
            }
        });

        // PLATE 포스트 관련 설정
        postList.setTitle("PLATE 포스트");
        postList.setBackgroundColor(Color.BLACK, Color.WHITE);
        postList.setMarginTop(80);
        postList.setMarginBottom(80);
        postList.setType(HomeAwardsList.TYPE_MODE.POST_MODE);

        // 이미지 슬라이더 관련 설정
        imageSlider.hideTitle();
        imageSlider.setType(HomeAwardsList.TYPE_MODE.IMAGE_SLIDE_MODE);
        imageSlider.removePadding();

        // ChipList를 불러올 때 겹치지 않고 불러오기 위해서 미리 배열 선언
        FoodStyle[] foodStyles = FoodStyle.randomFoodStyles(2);

        // Firebase를 통한 데이터를 불러오는 함수
        loadRecentList(recentList);
        loadPlatePost(postList);
        loadImageSlider(imageSlider);
        loadChipList(foodStyles[0], chipTypeList[0]);
        loadChipList(foodStyles[1], chipTypeList[1]);
        loadFoodTypeList("한식 다이닝", foodTypeList);
        loadTodayFood(todayFood);

        // 유저가 게스트인지 호스트인지 알아온다.
        // 값은 userType String에 저장됨.
        getUserType();

        //region 지원하기 카드뷰 관련 코드
        Picasso.get().load("https://firebasestorage.googleapis.com/v0/b/plate-f5144.appspot.com/o/chef.png?alt=media&token=2e0e6f43-2523-482e-82ff-f5cd5ad54e19")
                .fit().centerCrop().into(applyImage);
        //endregion
        return v;
    }

    private void initCompleteLoadedArray() {
        // 모든 값이 불러왔는지 확인하는 배열 초기화
        completeLoaded = new boolean[LIST_COUNT];
        for (int i = 0; i < LIST_COUNT; i++)
            completeLoaded[i] = false;
        // 뷰가 생성되면 불러온 리스트의 수를 0으로 초기화
        current = 0;
    }

    /**
     * 다이닝 Key 값에서 Timestamp의 값을 추출하는 함수
     * @param dataSnapshot 추출하고자하는 다이닝의 Datasnapshot
     * @return 다이닝 Key 값에 적혀있는 Timestamp 값
     */
    private Long getTimestamp(DataSnapshot dataSnapshot) {
        String key = dataSnapshot.getKey();
        return Long.parseLong(key.substring(key.lastIndexOf("-") + 1));
    }

    /**
     * DataSnapshot의 루트를 불러와 자식 노드를 TimeStamp에 맞춰 최신 순으로 정렬하는 메소드
     * 다이닝 Key 값이 HostUID-Timestamp로 이루어진 것을 이용하여 정렬하는 것이다.
     * @param root 다이닝 루트 노드
     * @return 루트 노드의 자식을 최신순으로 정렬한 배열
     */
    private DataSnapshot[] sortSnapshot(DataSnapshot root) {
        // root의 자식을 불러와 ArrayList에 저장
        List<DataSnapshot> temp = new ArrayList<>();
        for (DataSnapshot snapshot : root.getChildren()) {
            temp.add(snapshot);
        }

        // ArrayList를 배열로 변환
        DataSnapshot[] array = temp.toArray(new DataSnapshot[temp.size()]);

        // Sorting을 하기 위한 임시 데이터 변수
        DataSnapshot tmp;

        // Selection Sort를 이용하여 DataSnapshot을 정렬
        for (int i = 0; i < array.length - 1; i++) {
            for (int j = i + 1; j < array.length; j++) {
                if (getTimestamp(array[i]) < getTimestamp(array[j])) {
                    tmp = array[i];
                    array[i] = array[j];
                    array[j] = tmp;
                }
                for (DataSnapshot snapshot : array) Log.d(LogTags.IMPORTANT, getTimestamp(snapshot) + " at i :" + i + ", j : " + j);
            }
        }

        return array;
    }

    private void loadRecentList(final HomeHorizontalList mList) {
        mList.setTitle("최근에 올라온 음식이에요.");
        mDatabase.child("Dining").limitToLast(10).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshots) {

                for (DataSnapshot snapshot : sortSnapshot(snapshots)) {
                    HomeCardData data = snapshot.getValue(HomeCardData.class);
                    data.setImageUri(snapshot.getKey() + "/" + snapshot.child("images/1").getValue(String.class));
                    mList.addData(data);
                }

                completeLoaded[current++] = true;
                checkAllLoaded();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    private void loadPlatePost(final HomeAwardsList mList) {
        mDatabase.child("Home").child("Plate Post").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshots) {
                for (DataSnapshot snapshot : snapshots.getChildren()) {
                    HomeAwardsData data = snapshot.getValue(HomeAwardsData.class);
                    mList.addData(data);
                }

                completeLoaded[current++] = true;
                checkAllLoaded();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    private void loadImageSlider(final HomeAwardsList mList) {
        mDatabase.child("Home").child("Slider").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshots) {
                for (DataSnapshot snapshot : snapshots.getChildren()) {
                    ImageSlideData data = new ImageSlideData();
                    data.setImage(snapshot.getValue(String.class));
                    mList.addData(data);
                }

                completeLoaded[current++] = true;
                checkAllLoaded();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    private void loadChipList(final FoodStyle foodStyle, final HomeHorizontalList mList) {
        String title = "<font color=#4150b4>#" + KoreanUtil.includeGrammarCheck(foodStyle.label, "</font>은 ", "</font>는 ")
                + "어떤가요?";
        mList.setTitle(Html.fromHtml(title));
        mDatabase.child("Dining").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshots) {
                for (DataSnapshot snapshot : snapshots.getChildren()) {
                    if (((List<String>)snapshot.child("style").getValue()).contains(foodStyle.toString())) {
                        HomeCardData data = snapshot.getValue(HomeCardData.class);
                        data.setImageUri(snapshot.getKey() + "/" + snapshot.child("images/1").getValue(String.class));
                        mList.addData(data);
                    }
                }

                completeLoaded[current++] = true;
                checkAllLoaded();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    private void loadFoodTypeList(final String type, final HomeHorizontalList mList) {
        mList.setTitle(type + " 모음");
        mDatabase.child("Dining").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshots) {
                for (DataSnapshot snapshot : snapshots.getChildren()) {
                    if (snapshot.child("subtitle").getValue(String.class).equals(type)) {
                        HomeCardData data = snapshot.getValue(HomeCardData.class);
                        data.setImageUri(snapshot.getKey() + "/" + snapshot.child("images/1").getValue(String.class));
                        mList.addData(data);
                    }
                }
                Log.d(LogTags.POINT, current + "");
                completeLoaded[current++] = true;
                checkAllLoaded();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    private void loadTodayFood(final HomeTodayFood view) {

        mDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String path = snapshot.child("Home").child("Todays Food").getValue(String.class);
                String title = snapshot.child("Dining").child(path).child("title").getValue(String.class);
                String image = path + "/" + snapshot.child("Dining").child(path).child("images/1").getValue(String.class);
                view.launch(title, image);

                completeLoaded[current++] = true;
                checkAllLoaded();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    private void checkAllLoaded() {
        if (completeLoaded[LIST_COUNT - 1]) {
            // 로딩화면을 감춘 뒤 루프 종료
            loadingSkeletonView.setVisibility(View.GONE);
            loadingSkeletonView.setSpeed(0);

            // 데이터 화면 보여줌.
            scrollView.setVisibility(View.VISIBLE);

            // 검색창을 보여줌.
            searchButton.setVisibility(View.VISIBLE);

            // Loaded Array를 다시 전부 초기화
            initCompleteLoadedArray();

            // 만약에 Swipe를 통해서 Refreshing을 하고 있던거였으면 false로 바꿈.
            if (swipeRefreshLayout.isRefreshing()) {
                swipeRefreshLayout.setRefreshing(false);
            }
        }
    }

    /**
     * 유저가 Guest인지 Host인지를 구분하여
     * 유저가 Guest인 경우 지원하기 CardView를 보여주고
     * 유저가 Host인 경우 다이닝 추가하기 CardView를 보여준다.
     */
    private void getUserType() {
        final String userUID = FirebaseAuth.getInstance().getCurrentUser().getUid();

        mDatabase.child("User").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String userType = snapshot.child("Guest").hasChild(userUID) ? "Guest" : "Host";

                if (userType.equals("Host"))
                    applyCardView.setVisibility(View.GONE);

                if (userType.equals("Guest"))
                    addDiningPlanCardView.setVisibility(View.GONE);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    @Override
    public void onRefresh() {
        // 로딩화면 표시
        loadingSkeletonView.setVisibility(View.VISIBLE);
        loadingSkeletonView.setSpeed(1f);
        scrollView.setVisibility(View.GONE);
        searchButton.setVisibility(View.GONE);

        // 모든 리스트 데이터 삭제
        recentList.removeAllData();
        postList.removeAllData();
        imageSlider.removeAllData();
        chipTypeList[0].removeAllData();
        chipTypeList[1].removeAllData();
        foodTypeList.removeAllData();

        FoodStyle[] styles = FoodStyle.randomFoodStyles(2);

        // 리스트에 값 다시 입력
        loadRecentList(recentList);
        loadPlatePost(postList);
        loadImageSlider(imageSlider);
        loadChipList(styles[0], chipTypeList[0]);
        loadChipList(styles[1], chipTypeList[1]);
        loadFoodTypeList("한식 다이닝", foodTypeList);
        loadTodayFood(todayFood);
    }
}
