package org.connectbot;

import android.app.Activity;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.EditText;

/**
 * Created by adb on 10/08/2015.
 */
public class CopyEditActivity extends Activity
{
  public final static String TAG = CopyEditActivity.class.getSimpleName();
  public static final String ARG_BUFFER = "buffer";
  private EditText mEditText;

  @Override
  protected void onCreate( Bundle savedInstanceState )
  {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.act_copyedit);

    ActionBarWrapper actionBar = ActionBarWrapper.getActionBar(this);
    actionBar.setDisplayHomeAsUpEnabled(true);

    Bundle bundle = getIntent().getExtras();
    String buffer = bundle.getString(ARG_BUFFER);

    if(buffer != null)
    {
      mEditText = (EditText) findViewById(R.id.editText);
      mEditText.setText(buffer);
      mEditText.setSelection(buffer.length());
    }
  }

  @Override
  public boolean onOptionsItemSelected( MenuItem item )
  {
    switch(item.getItemId())
    {
      case android.R.id.home:
        finish();
        return true;
    }
    return super.onOptionsItemSelected(item);
  }

}
