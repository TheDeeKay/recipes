package co.bstorm.aleksa.recipes.ui.activity;

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SearchView;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;

import java.util.ArrayList;

import co.bstorm.aleksa.recipes.R;
import co.bstorm.aleksa.recipes.api.API;
import co.bstorm.aleksa.recipes.api.FetchData;
import co.bstorm.aleksa.recipes.constants.Constants;
import co.bstorm.aleksa.recipes.constants.DbColumns;
import co.bstorm.aleksa.recipes.pojo.Recipe;
import co.bstorm.aleksa.recipes.ui.adapter.RecipeListAdapter;
import co.bstorm.aleksa.recipes.util.FilterUtils;
import io.realm.Realm;
import io.realm.RealmChangeListener;
import io.realm.RealmConfiguration;
import io.realm.RealmQuery;
import io.realm.RealmResults;
import rx.Observable;
import rx.Observer;
import rx.Subscriber;
import rx.subscriptions.CompositeSubscription;

public class MainActivity extends AppCompatActivity implements SearchView.OnQueryTextListener{

    private static final String TAG = "MainActivity";

    private Realm realm;
    private RealmChangeListener<Realm> changeListener;
    private RealmResults<Recipe> recipes;
    private RealmResults<Recipe> filteredRecipes;

    private RecipeListAdapter mAdapter;
    private ListView mRecipesList;

    private Button mClearFilters;

    private Menu mMenu;

    // Used to unsubscribe from observables at the end of lifecycle
    private CompositeSubscription cs;

    // Indicates whether we are currently loading more items
    private boolean flagLoading = false;

    // Indicates whether we are currently using the search query
    // Used to disable new fetching on scroll down while using search
    private boolean queryActive = false;

    // Used to memorize the search while moving between activities and filters
    private String lastQuery = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        realm = Realm.getDefaultInstance();

        cs = new CompositeSubscription();

        // Fetch all the data (recipes, tags and components) and add their subscriptions to the composite sub
        cs.add(FetchData.fetchDataFromObservable(API.getOffsetRecipes(0), getApplicationContext()));
        cs.add(FetchData.fetchDataFromObservable(API.getAllComponents(), getApplicationContext()));
        cs.add(FetchData.fetchDataFromObservable(API.getAllTags(), getApplicationContext()));

        recipes = realm.where(Recipe.class).findAll();
        filteredRecipes = recipes;

        mRecipesList = (ListView) findViewById(R.id.recipes_list);
        mAdapter = new RecipeListAdapter(this, recipes);
        mRecipesList.setAdapter(mAdapter);

        mClearFilters = (Button) findViewById(R.id.clear_filters);
        mClearFilters.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FilterUtils.filterTagIds.clear();
                filteredRecipes = applyFilters(recipes);
                if (queryActive)
                    mAdapter.updateData(doQuery(lastQuery, filteredRecipes));
                else
                    mAdapter.updateData(filteredRecipes);
            }
        });

        // Set a footer view to be a simple progress bar
        final ProgressBar progressBar = new ProgressBar(this);
        progressBar.getIndeterminateDrawable().setColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN);
        progressBar.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.progress_bar_background, null));

        // Monitor Realm data
        changeListener = new RealmChangeListener<Realm>() {
            @Override
            public void onChange(Realm element) {
                mAdapter.notifyDataSetChanged();
            }
        };
        realm.addChangeListener(changeListener);

        mRecipesList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                launchDetails(id);
            }
        });

        mRecipesList.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem,
                                 final int visibleItemCount, final int totalItemCount) {

                if(totalItemCount != 0 && firstVisibleItem + visibleItemCount == totalItemCount) {
                    // If we're already loading, or currently querying/filtering, we won't load new data
                    if(!flagLoading && !queryActive && FilterUtils.filterTagIds.isEmpty()) {
                        flagLoading = true;

                        mRecipesList.addFooterView(progressBar);

                        // Get an observable for fetching offset recipes
                        Observable<ArrayList<Recipe>> observable = API.getOffsetRecipes(totalItemCount);
                        // Get an Observer interface for saving data to Realm
                        final Observer<ArrayList<Recipe>> observer =
                                FetchData.getObserverInterface(observable, getApplicationContext());

                        // Fetch data, store it to Realm, set flagLoading to false and store the subscription
                        cs.add(FetchData.prepareObservable(observable, getApplicationContext())
                                .subscribe(new Subscriber<ArrayList<Recipe>>() {
                                    @Override
                                    public void onCompleted() {
                                        observer.onCompleted();

                                        Realm newRealm = Realm.getInstance(new RealmConfiguration.Builder(MainActivity.this).build());
                                        long count = newRealm.where(Recipe.class).count();
                                        newRealm.close();
                                        // If we fetched anything, we just remove the loading flag
                                        // otherwise, we disable further updates by keeping the loading flag
                                        if (count > totalItemCount)
                                            flagLoading = false;
                                        // Remove the progress
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                mRecipesList.removeFooterView(progressBar);
                                                mAdapter.notifyDataSetChanged();
                                            }
                                        });
                                    }
                                    @Override
                                    public void onError(Throwable e) {
                                        observer.onError(e);
                                        flagLoading = false;
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                mRecipesList.removeFooterView(progressBar);
                                                mAdapter.notifyDataSetChanged();
                                            }
                                        });
                                    }
                                    @Override
                                    public void onNext(ArrayList<Recipe> recipes) {
                                        observer.onNext(recipes);
                                    }
                                }));
                    }
                }
            }
        });
    }

    /**
     * Launches the details activity for the given recipe ID
     * @param id ID of the recipe whose details we want
     */
    private void launchDetails(long id){
        Intent detailsIntent = new Intent(this, DetailActivity.class);
        detailsIntent.putExtra(Constants.RECIPE_ID_EXTRA, id);
        startActivity(detailsIntent);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Apply any selected filters (or clear them if there are none)
        filteredRecipes = applyFilters(recipes);

        // Apply the search
        if (queryActive && lastQuery != null) {
            SearchView search = (SearchView) mMenu.findItem(R.id.main_search).getActionView();
            search.setQuery(lastQuery, true);
            mAdapter.updateData(doQuery(lastQuery, filteredRecipes));
        }
        else
            mAdapter.updateData(filteredRecipes);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.options_menu, menu);

        mMenu = menu;

        SearchManager manager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        SearchView mSearchView = (SearchView) MenuItemCompat.getActionView(menu.findItem(R.id.main_search));

        mSearchView.setSearchableInfo(manager.getSearchableInfo(getComponentName()));
        mSearchView.setOnQueryTextListener(this);
        mSearchView.setIconifiedByDefault(true);
        mSearchView.setSubmitButtonEnabled(false);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){

            case R.id.main_filter:{
                Intent intent = new Intent(this, FilterActivity.class);
                startActivity(intent);

                return true;
            }

            case R.id.main_shopping:{
                Intent intent = new Intent(this, ShoppingActivity.class);
                startActivity(intent);
            }
            default: return false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        cs.unsubscribe();

        recipes.removeChangeListener(changeListener);

        realm.close();
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        if (mMenu != null)
            mMenu.findItem(R.id.main_search).getActionView().clearFocus();
        return true;
    }

    @Override
    public boolean onQueryTextChange(String newText) {

        mAdapter.updateData(doQuery(newText, filteredRecipes));

        return true;
    }

    /**
     * Applies search to a given query result
     * @param newText The search keyword to apply
     * @param base The base query to modify
     * @return Query results to which the search has been applied
     */
    private RealmResults<Recipe> doQuery(String newText, RealmResults<Recipe> base){
        lastQuery = newText;
        if (newText != null && newText.length() > 0) {
            queryActive = true;
            String query = newText.toLowerCase();

            // A hack since Realm doesn't support case insensitive search for non-english locales
            return base.where().contains(DbColumns.Recipe.TITLE_LOWER, query).findAll();
        }
        else {
            queryActive = false;
            return base;
        }
    }

    /**
     * Applies filters to given query results
     * @param base The results to which we want to apply filters
     * @return
     */
    private RealmResults<Recipe> applyFilters(RealmResults<Recipe> base){
        if (!FilterUtils.filterTagIds.isEmpty()) {

            mClearFilters.setVisibility(View.VISIBLE);

            RealmQuery<Recipe> filteredRecipesQuery = base.where();

            boolean passedFirstItem = false;
            for (Integer id : FilterUtils.filterTagIds) {
                if (passedFirstItem)
                    filteredRecipesQuery = filteredRecipesQuery.or();
                else
                    passedFirstItem = true;
                filteredRecipesQuery = filteredRecipesQuery.equalTo(DbColumns.Recipe.TAGS + "." + DbColumns.RecipeTag.ID, id);

            }
            return filteredRecipesQuery.findAll();
        }
        else {
            mClearFilters.setVisibility(View.GONE);
            return base;
        }
    }
}
